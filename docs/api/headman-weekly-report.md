# Headman Weekly Report Export API

M18 adds export of the headman's weekly attendance report. The API never accepts
`groupId` from the client: the group is resolved from the authenticated request
context, and access is allowed only for `STUDENT` users with `is_headman=true`.

## Week Numbering

Week 1 is the Monday-Sunday week that contains the active semester `date_from`.
The list continues by Mondays through the week that contains `date_to`, even if
the semester starts or ends in the middle of a week.

Example: active semester `2026-04-29..2026-05-12` produces:

| weekOfSemester | weekStart | weekEnd |
|---:|---|---|
| 1 | 2026-04-27 | 2026-05-03 |
| 2 | 2026-05-04 | 2026-05-10 |
| 3 | 2026-05-11 | 2026-05-17 |

## Endpoints

### List Weeks

```http
GET /api/attendance/reports/headman-weekly/weeks
```

Response `200`:

```json
{
  "semesterId": 1,
  "semesterName": "Spring 2026",
  "semesterDateFrom": "2026-04-29",
  "semesterDateTo": "2026-05-12",
  "weeks": [
    {
      "weekOfSemester": 1,
      "isoWeek": 18,
      "label": "Н1",
      "weekStart": "2026-04-27",
      "weekEnd": "2026-05-03",
      "current": true
    }
  ],
  "_links": {
    "self": {
      "href": "/api/attendance/reports/headman-weekly/weeks"
    }
  }
}
```

### Export One Week

```http
GET /api/attendance/reports/headman-weekly/current?weekStart=2026-04-27&format=docx
```

`format`: `docx`, `pdf`, `png`.

### Export Selected Weeks

```http
POST /api/attendance/reports/headman-weekly/export
Content-Type: application/json
```

```json
{
  "weekStarts": ["2026-04-27", "2026-05-11"],
  "format": "pdf"
}
```

The selected weeks may be non-consecutive.

## File Responses

All successful exports return `Content-Disposition: attachment` with an UTF-8
filename.

| Case | Content-Type | Filename |
|---|---|---|
| single `docx` | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | `{groupCode}_{dateFrom}_{dateTo}.docx` |
| single `pdf` | `application/pdf` | `{groupCode}_{dateFrom}_{dateTo}.pdf` |
| single `png` | `image/png` | `{groupCode}_{dateFrom}_{dateTo}.png` |
| multi `docx` | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | `{groupCode}_{dateFrom}_{dateTo}.docx` |
| multi `pdf` | `application/pdf` | `{groupCode}_{dateFrom}_{dateTo}.pdf` |
| multi `png` | `application/zip` | `{groupCode}_{dateFrom}_{dateTo}_png.zip` |

If selected weeks are not consecutive, `_С_ПРОПУСКАМИ` is added before the
extension or before `_png.zip`.

## Errors

All errors use RFC 9457 Problem Details via the shared `ErrorResponse`.

| Status | Reason |
|---:|---|
| 400 | unknown `format` or invalid JSON/body validation |
| 403 | user is not a headman of their group |
| 422 | selected week is outside the active semester, or template limits are exceeded |
| 503 | academic/schedule/renderer service is unavailable |
