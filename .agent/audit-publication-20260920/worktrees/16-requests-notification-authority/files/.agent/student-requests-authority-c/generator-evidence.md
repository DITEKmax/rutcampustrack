# Protobuf generator evidence

Date: 2026-09-08. Product generated files were refreshed from the scoped
`proto/attendance.proto` only.

- Source SHA-256: `54CC1E7EFFAB9860390555FA2B345798230A884E2F1E1974F0931DAF34D27D2B`.
- Generator: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/services/notification-bot/.venv/Scripts/python.exe`.
- Runtime: Python `3.13.5`, `grpcio-tools==1.73.0`.
- Staging source: `.agent/student-requests-authority-c/proto-gen/bot/grpc_client/attendance.proto`.
- Command (exit code `0`):
  `python -m grpc_tools.protoc -I .agent/student-requests-authority-c/proto-gen --python_out .agent/student-requests-authority-c/proto-gen --grpc_python_out .agent/student-requests-authority-c/proto-gen .agent/student-requests-authority-c/proto-gen/bot/grpc_client/attendance.proto`
- Generated staging output SHA-256: `attendance_pb2.py`
  `5247912C39E1B6F04C31F5C358DDA6EFAEE04BF1B127D831E81582EB6F507DA6`;
  `attendance_pb2_grpc.py`
  `40738D25B754F358C2AF36B1FB9A2261EAC483F7503E981E74C2C921D41C0C91`.
- Product output hashes match staging byte-for-byte. The generated gRPC module
  contains `from bot.grpc_client import attendance_pb2 ...`, which is required
  by the package import layout.

No generated file was hand-edited.
