package hlog

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestFormatAttributes_JSONStringExpansionAllowlist(t *testing.T) {
	got := FormatAttributes("url.query_params", `{"foo":"bar","baz":"qux"}`)
	assert.Equal(t, map[string]string{
		"url.query_params.foo": "bar",
		"url.query_params.baz": "qux",
	}, got)
}

func TestFormatAttributes_JSONStringNotInAllowlist(t *testing.T) {
	raw := `{"foo":"bar"}`
	got := FormatAttributes("http.request.body", raw)
	assert.Equal(t, map[string]string{"http.request.body": raw}, got)
}

func TestFormatAttributes_NonJSONAllowlistedKey(t *testing.T) {
	got := FormatAttributes("url.query_params", "not-json")
	assert.Equal(t, map[string]string{"url.query_params": "not-json"}, got)
}

func TestFormatAttributes_InvalidJSONAllowlistedKey(t *testing.T) {
	raw := `{"foo":`
	got := FormatAttributes("url.query_params", raw)
	assert.Equal(t, map[string]string{"url.query_params": raw}, got)
}

func TestFormatAttributes_LeadingWhitespaceJSON(t *testing.T) {
	got := FormatAttributes("url.query_params", `   {"foo":"bar"}`)
	assert.Equal(t, map[string]string{"url.query_params.foo": "bar"}, got)
}

func TestFormatAttributes_NestedJSONObjectInAllowlist(t *testing.T) {
	got := FormatAttributes("url.query_params", `{"filter":{"id":"1"}}`)
	assert.Equal(t, map[string]string{"url.query_params.filter.id": "1"}, got)
}

func TestFormatAttributes_PassesThroughForOtherTypes(t *testing.T) {
	got := FormatAttributes("count", int64(7))
	assert.Equal(t, map[string]string{"count": "7"}, got)

	got = FormatAttributes("ratio", 1.5)
	assert.Equal(t, map[string]string{"ratio": "1.5"}, got)

	got = FormatAttributes("ok", true)
	assert.Equal(t, map[string]string{"ok": "true"}, got)
}

func TestFormatAttributes_NestedMapStillFlattens(t *testing.T) {
	got := FormatAttributes("a", map[string]interface{}{
		"b": map[string]interface{}{"c": "d"},
	})
	assert.Equal(t, map[string]string{"a.b.c": "d"}, got)
}
