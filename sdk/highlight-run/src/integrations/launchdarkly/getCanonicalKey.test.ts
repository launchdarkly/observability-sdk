import { expect, it } from 'vitest';
import { getCanonicalKey } from './index';

it.each([
    [{ key: 'bob'}, 'bob'],
    [{ kind: 'org', key: 'org123'}, 'org:org123'],
    [{ key: 'Org:%Key%'}, 'org:Org%3A%25Key%25'],
    [{
        kind: 'multi',
        user: {
          key: 'user-key',
          name: 'Test User',
        },
        org: {
          key:
           'org-key',
          name: 'Test Org',
        },
      }, 'org:org-key:user:user-key'],
])("should produce the correct canonical key for a given context", (context: any, canonicalKey: string) => {
    const result = getCanonicalKey(context);
    expect(result).toBe(canonicalKey);
});
