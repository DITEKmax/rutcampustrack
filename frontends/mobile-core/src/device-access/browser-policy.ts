export function browserDeviceContextAllows(feature: 'camera' | 'geolocation'): boolean {
  const policy =
    typeof document === 'undefined'
      ? undefined
      : (document as Document & {
          permissionsPolicy?: { allowsFeature(feature: string): boolean }
          featurePolicy?: { allowsFeature(feature: string): boolean }
        })
  return (
    globalThis.isSecureContext !== false &&
    (policy?.permissionsPolicy ?? policy?.featurePolicy)?.allowsFeature(feature) !== false
  )
}
