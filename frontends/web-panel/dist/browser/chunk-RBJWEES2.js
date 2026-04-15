import {
  HttpErrorResponse
} from "./chunk-FLE4DLW4.js";

// src/app/features/student/shared/format-load-error.ts
function formatLoadError(err, prefix = "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0434\u0430\u043D\u043D\u044B\u0435.") {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return `${prefix} \u041D\u0435\u0442 \u0441\u0432\u044F\u0437\u0438 \u0441 \u0441\u0435\u0440\u0432\u0435\u0440\u043E\u043C.`;
    }
    if (err.status === 401 || err.status === 403) {
      return `${prefix} \u0421\u0435\u0441\u0441\u0438\u044F \u0438\u0441\u0442\u0435\u043A\u043B\u0430 \u0438\u043B\u0438 \u043D\u0435\u0442 \u043F\u0440\u0430\u0432. \u0412\u043E\u0439\u0434\u0438\u0442\u0435 \u0441\u043D\u043E\u0432\u0430.`;
    }
    if (err.status >= 500) {
      return `${prefix} \u0421\u0435\u0440\u0432\u0435\u0440 \u0432\u0440\u0435\u043C\u0435\u043D\u043D\u043E \u043D\u0435\u0434\u043E\u0441\u0442\u0443\u043F\u0435\u043D (${err.status}).`;
    }
    return `${prefix} (HTTP ${err.status})`;
  }
  return prefix;
}

export {
  formatLoadError
};
//# sourceMappingURL=chunk-RBJWEES2.js.map
