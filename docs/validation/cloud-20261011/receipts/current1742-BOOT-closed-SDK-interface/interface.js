/* Pure fixed-path interface seam. No SDK, file, output, dynamic-key enumeration. */
function bootInterface() {
  const cap = 33554432;
  const missing = Symbol('missing');
  const blocked = () => { throw new Error('BOOT_INTERFACE_BLOCKED'); };
  function field(object, name) {
    if (object === missing || object === null || typeof object !== 'object') return missing;
    const d = Object.getOwnPropertyDescriptor(object, name);
    if (!d) return missing;
    if (!Object.prototype.hasOwnProperty.call(d, 'value')) blocked();
    return d.value;
  }
  function kind(value) {
    if (value === missing) return 'MISSING';
    if (value === null) return 'NULL';
    if (Array.isArray(value)) return 'ARRAY';
    return ({undefined:'UNDEFINED',boolean:'BOOLEAN',number:'NUMBER',string:'STRING',object:'OBJECT'})[typeof value] || 'UNSUPPORTED';
  }
  function shape(value) {
    const record = {kind:kind(value)};
    if (typeof value === 'string') record.string_units = Math.min(value.length, cap+1);
    if (Array.isArray(value)) {
      const length = field(value, 'length');
      if (!Number.isSafeInteger(length) || length < 0) blocked();
      record.array_count = Math.min(length, 33);
      record.first4 = [];
      for (let i=0;i<Math.min(length,4);i++) {
        const item = field(value, String(i));
        record.first4.push({kind:kind(item), type:kind(field(item,'type')),
          text:shapeScalar(field(item,'text'))});
      }
    }
    return record;
  }
  function shapeScalar(value) {
    return typeof value === 'string' ? {kind:'STRING',string_units:Math.min(value.length,cap+1)} : {kind:kind(value)};
  }
  function paths(result) {
    const structured = field(result,'structuredContent');
    const nested = field(structured,'structuredContent');
    return {result, structured, nested};
  }
  function inspect(result) {
    try {
      const p = paths(result);
      return {state:'CLOSED_INTERFACE_SHAPE',schema:1,
        result:shape(p.result), result_content:shape(field(p.result,'content')),
        result_isError:shapeScalar(field(p.result,'isError')),
        structuredContent:shape(p.structured),
        structuredContent_content:shape(field(p.structured,'content')),
        structuredContent_structuredContent:shape(p.nested),
        structuredContent_structuredContent_content:shape(field(p.nested,'content')),
        payload_selected:false, actual_log_obtained:false, cause:'UNKNOWN'};
    } catch (_) { return {state:'BOOT_INTERFACE_BLOCKED',payload_selected:false,cause:'UNKNOWN'}; }
  }
  function approvedPayload(result, rootApprovedSchema) {
    try {
      // Only Root may bind ONE of these exact direct-string schemas after review
      // of the closed observed shape. No text-block fallback or nestedJSON guess.
      if (!['SC_CONTENT_TIMESTAMPED_TEXT','SC_SC_CONTENT_TIMESTAMPED_TEXT'].includes(rootApprovedSchema)) blocked();
      const error = field(result,'isError');
      if (error !== missing && error !== false) blocked();
      const p = paths(result);
      if (kind(p.result)!=='OBJECT' || kind(p.structured)!=='OBJECT') blocked();
      const owner = rootApprovedSchema === 'SC_CONTENT_TIMESTAMPED_TEXT' ? p.structured : p.nested;
      if (kind(owner)!=='OBJECT') blocked();
      const other = field(rootApprovedSchema === 'SC_CONTENT_TIMESTAMPED_TEXT' ? p.nested : p.structured,'content');
      if (typeof other === 'string') blocked(); // No arbitrary priority between two candidates.
      const text = field(owner,'content');
      if (typeof text !== 'string' || text.length===0 || text.length>cap) blocked();
      // Narrow framing admission only, not cause/authenticity/full-log parsing.
      if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,7})?Z /.test(text.slice(0,64))) blocked();
      return text; // PRIVATE RAM ONLY; feed the unchanged original carrier encoder.
    } catch (_) { throw new Error('BOOT_INTERFACE_BLOCKED'); }
  }
  return {inspect, approvedPayload};
}
