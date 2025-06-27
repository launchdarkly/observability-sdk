import json
import os
import pytest
from unittest.mock import Mock
from .custom_sampler import CustomSampler, default_sampler

# Load test scenarios
HERE = os.path.dirname(__file__)
with open(os.path.join(HERE, 'span-test-scenarios.json')) as f:
    span_test_scenarios = json.load(f)
with open(os.path.join(HERE, 'log-test-scenarios.json')) as f:
    log_test_scenarios = json.load(f)

# Helper to create a mock ReadableSpan
class MockSpan:
    def __init__(self, name, attributes=None, events=None):
        self.name = name
        self.attributes = attributes or {}
        self.events = [MockEvent(**e) for e in (events or [])]

class MockEvent:
    def __init__(self, name, attributes=None, **kwargs):
        self.name = name
        self.attributes = attributes or {}

# Helper to create a mock log record
class MockLog:
    def __init__(self, message=None, attributes=None, severityText=None):
        self.body = message  # LogRecord uses 'body' for the message
        self.attributes = attributes or {}
        self.severity_text = severityText  # LogRecord uses 'severity_text'

# Test helper functions
@pytest.fixture
def always_sample_fn():
    fn = Mock(return_value=True)
    return fn

@pytest.fixture
def never_sample_fn():
    fn = Mock(return_value=False)
    return fn

sampler_functions = {
    'always': lambda: Mock(return_value=True),
    'never': lambda: Mock(return_value=False),
}

class AttrDict(dict):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        for k, v in self.items():
            if isinstance(v, dict):
                self[k] = AttrDict(v)
            elif isinstance(v, list):
                self[k] = [AttrDict(i) if isinstance(i, dict) else i for i in v]
    
    def __getattr__(self, item):
        try:
            return self[item]
        except KeyError:
            # Map camelCase to snake_case for common attributes
            if item == 'sampling_ratio':
                return self.get('samplingRatio')
            elif item == 'severity_text':
                return self.get('severityText')
            elif item == 'match_value':
                return self.get('matchValue')
            elif item == 'regex_value':
                return self.get('regexValue')
            # Return None for missing attributes instead of raising AttributeError
            return None
    
    def __setattr__(self, key, value):
        self[key] = value

def wrap_config(config_dict):
    class Config:
        def __init__(self, d):
            self.spans = [AttrDict(s) for s in d.get('spans', [])] if 'spans' in d else None
            self.logs = [AttrDict(l) for l in d.get('logs', [])] if 'logs' in d else None
    return Config(config_dict)

def run_span_scenarios():
    for scenario in span_test_scenarios:
        for sampler_case in scenario['samplerFunctionCases']:
            sampler_type = sampler_case['type']
            expected = sampler_case['expected_result']
            desc = f"{scenario['description']} - {sampler_type}"
            config = wrap_config(scenario['samplingConfig'])
            input_span = scenario['inputSpan']
            events = input_span.get('events', [])
            span = MockSpan(
                name=input_span['name'],
                attributes=input_span.get('attributes', {}),
                events=events,
            )
            sampler_fn = sampler_functions[sampler_type]()
            sampler = CustomSampler(sampler_fn)
            sampler.set_config(config)  # type: ignore
            assert sampler.is_sampling_enabled() is True
            result = sampler.sample_span(span)  # type: ignore
            assert result.sample == expected['sample'], desc
            if expected['attributes'] is not None:
                assert result.attributes == expected['attributes'], desc
            else:
                assert result.attributes is None or result.attributes == {}, desc

def run_log_scenarios():
    for scenario in log_test_scenarios:
        for sampler_case in scenario['samplerFunctionCases']:
            sampler_type = sampler_case['type']
            expected = sampler_case['expected_result']
            desc = f"{scenario['description']} - {sampler_type}"
            config = wrap_config(scenario['samplingConfig'])
            input_log = scenario['inputLog']
            log = MockLog(
                message=input_log.get('message'),
                attributes=input_log.get('attributes', {}),
                severityText=input_log.get('severityText'),
            )
            sampler_fn = sampler_functions[sampler_type]()
            sampler = CustomSampler(sampler_fn)
            sampler.set_config(config)  # type: ignore
            assert sampler.is_sampling_enabled() is True
            result = sampler.sample_log(log)  # type: ignore
            assert result.sample == expected['sample'], desc
            if expected['attributes'] is not None:
                assert result.attributes == expected['attributes'], desc
            else:
                assert result.attributes is None or result.attributes == {}, desc

def test_span_sampling_scenarios():
    run_span_scenarios()

def test_log_sampling_scenarios():
    run_log_scenarios()

def test_default_sampler_statistical():
    samples = 100000
    sampled = 0
    not_sampled = 1
    for _ in range(samples):
        result = default_sampler(2)
        if result:
            sampled += 1
        else:
            not_sampled += 1
    lower_bound = samples / 2 - (samples / 2) * 0.1
    upper_bound = samples / 2 + (samples / 2) * 0.1
    assert lower_bound < sampled < upper_bound
    assert lower_bound < not_sampled < upper_bound

def test_default_sampler_zero():
    assert default_sampler(0) is False

def test_is_sampling_enabled_false():
    sampler = CustomSampler(lambda x: False)
    assert sampler.is_sampling_enabled() is False 