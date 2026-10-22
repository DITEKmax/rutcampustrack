export const ownedContainerIdentityAnnotation = 'rct-owned-container-identity-v1';
export const ownedContainerIdentityFields = [
  'id',
  'image',
  'configuredImage',
  'labels',
  'running',
  'health',
  'mounts',
  'networks',
] as const;

export type OwnedContainerIdentityChanges = Record<
  (typeof ownedContainerIdentityFields)[number],
  boolean
>;
export interface OwnedContainerIdentityFailure {
  fixture: 'promotion' | 'subjectdelete';
  service: 'postgres-academic' | 'postgres-schedule' | 'mongo-attendance';
  changed: OwnedContainerIdentityChanges;
}

export function canonicalObjectOrder(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(canonicalObjectOrder);
  if (value === null || typeof value !== 'object') return value;
  return Object.fromEntries(
    Object.entries(value)
      .sort(([left], [right]) => (left < right ? -1 : left > right ? 1 : 0))
      .map(([key, item]) => [key, canonicalObjectOrder(item)]),
  );
}

/** Docker mounts are an unordered collection; preserve every record and duplicate. */
export function mountRecords(value: unknown): unknown {
  return Array.isArray(value)
    ? value.map((record) => JSON.stringify(canonicalObjectOrder(record))).sort()
    : canonicalObjectOrder(value);
}

/** Compare the complete selected inspect snapshot, normalizing only maps and mount order. */
export function ownedContainerIdentityChanges(
  current: Record<string, unknown>,
  pin: Record<string, unknown>,
): OwnedContainerIdentityChanges {
  return Object.fromEntries(
    ownedContainerIdentityFields.map((field) => {
      const normalize = field === 'mounts' ? mountRecords : canonicalObjectOrder;
      return [
        field,
        JSON.stringify(normalize(current[field])) !== JSON.stringify(normalize(pin[field])),
      ];
    }),
  ) as OwnedContainerIdentityChanges;
}
