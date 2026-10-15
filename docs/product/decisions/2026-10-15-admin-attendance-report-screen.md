# D026 — Q023: Administrator group attendance screen and report contents

Received UTC 2026-10-15T13:10:09.499816+00:00 in main Cloud chat.

Owner exact answer: «Принять этот экран и состав (рекомендую)».

Implement the accepted current PWA/TMA screen «Посещаемость групп», opened from the administrator dashboard. It has a semester selector, group search and sorting. The group table contains group, attendance percentage, counted lessons and counts «+ / н / у». Compute on the server by the existing accepted statistics rules, excluding canceled lessons; distinguish no data from a genuine zero percentage. Export the selected report with its active filters in the existing five formats: HTML, PDF, DOCX, XLSX and PNG. Do not add the canceled red-zone/threshold report or student personal-data drilldown.

Use current accepted mobile components, tokens and shared PWA/TMA features/adapters; apply rutcampustrack-design before UI implementation. This resolves D025/Q020 screen and contents and authorizes the missing accepted function in this Cloud task. New web frontend and Figma publication remain outside scope. Compatible API/data aggregation and current authorization, asynchronous export/delivery/download behavior must be specified by their sole writer and tested by observable real paths. Do not silently broaden unrelated dashboard or registry exports.
