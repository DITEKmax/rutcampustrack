// src/app/features/admin/shared/types.ts
function fullName(u) {
  const mid = u.middleName ? ` ${u.middleName}` : "";
  return `${u.lastName} ${u.firstName}${mid}`;
}
function deriveSemesterStatus(s) {
  if (s.active)
    return "active";
  const today = (/* @__PURE__ */ new Date()).toISOString().slice(0, 10);
  return s.dateFrom > today ? "planned" : "finished";
}

export {
  fullName,
  deriveSemesterStatus
};
//# sourceMappingURL=chunk-OGMCFIRC.js.map
