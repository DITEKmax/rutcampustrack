/* Fixed observed SC.content transport seam; no SDK, parser, output or key enumeration. */
function bootBomPayload(existingInterface, result, rootApprovedSchema) {
  const blocked = () => { throw new Error('BOOT_INTERFACE_BLOCKED'); };
  try {
    if (rootApprovedSchema !== 'SC_CONTENT_TIMESTAMPED_TEXT') blocked();
    if (result === null || typeof result !== 'object' || Array.isArray(result)) blocked();
    const structured = Object.getOwnPropertyDescriptor(result,'structuredContent');
    if (!structured || !Object.prototype.hasOwnProperty.call(structured,'value')) blocked();
    const sc = structured.value;
    if (sc === null || typeof sc !== 'object' || Array.isArray(sc)) blocked();
    const descriptor = Object.getOwnPropertyDescriptor(sc,'content');
    if (!descriptor || !Object.prototype.hasOwnProperty.call(descriptor,'value')) blocked();
    const text = descriptor.value;
    if (typeof text !== 'string' || text.length===0 || text.length>33554432) blocked();
    const bomRemoved = text.charCodeAt(0)===0xfeff;
    const payload = bomRemoved ? text.slice(1) : text;
    if (payload.indexOf('\ufeff')!==-1) blocked(); // Double/interior signatures denied.
    // Preserve ONLY predefined descriptors needed by the immutable decoder;
    // never inspect/copy arbitrary keys or mask its error/ambiguity checks.
    const candidate = Object.create(null), owner = Object.create(null);
    Object.defineProperty(owner,'content',{value:payload});
    const nested = Object.getOwnPropertyDescriptor(sc,'structuredContent');
    if (nested) Object.defineProperty(owner,'structuredContent',nested);
    Object.defineProperty(candidate,'structuredContent',{value:owner});
    const error = Object.getOwnPropertyDescriptor(result,'isError');
    if (error) Object.defineProperty(candidate,'isError',error);
    return {privatePayload:existingInterface.approvedPayload(candidate,rootApprovedSchema),bomRemoved};
  } catch (_) { throw new Error('BOOT_INTERFACE_BLOCKED'); }
}
