# ADR-020 — Private source storage

Status: Accepted (approved Prompt 004).

User source images use private storage behind an application port. Production target is Cloudflare R2 with scoped expiring direct-upload authorization; local filesystem upload endpoints are restricted to local/test profiles. Keys are opaque, verification is server-controlled, and originals must remain immutable after upload. Ownership cookies protect source access; temporary bearer URLs never become permanent public links. Retention must be defined before production launch.
