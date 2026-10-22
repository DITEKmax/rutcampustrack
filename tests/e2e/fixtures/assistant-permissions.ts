import { strict as assert } from 'node:assert';

export function assistantPermissionCodes(value: unknown): string[] {
  assert(Array.isArray(value), 'Assistant permission contract is an array');
  return value.map((option) => {
    assert(
      option && typeof option === 'object' && typeof option.code === 'string',
      'Assistant permission option has a string code',
    );
    return option.code;
  });
}
