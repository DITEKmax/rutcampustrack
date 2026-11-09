/** Synchronous entry point: callers invoke this directly in the button's click turn. */
export function requestBrowserCamera(
  video: boolean | MediaTrackConstraints = true,
): Promise<MediaStream> {
  const policy =
    typeof document === 'undefined'
      ? undefined
      : (document as Document & {
          permissionsPolicy?: { allowsFeature(feature: string): boolean }
          featurePolicy?: { allowsFeature(feature: string): boolean }
        })
  if (
    globalThis.isSecureContext === false ||
    (policy?.permissionsPolicy ?? policy?.featurePolicy)?.allowsFeature('camera') === false
  )
    throw new DOMException('Camera blocked by document policy', 'SecurityError')
  if (!globalThis.navigator?.mediaDevices?.getUserMedia)
    throw new DOMException('Camera unsupported', 'NotSupportedError')
  return navigator.mediaDevices.getUserMedia({ video, audio: false })
}
