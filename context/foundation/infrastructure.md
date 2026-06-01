---
project: Apriary Production Tracking
researched_at: 2026-06-01
recommended_platform: Mikrus 3.5 (stay)
runner_up: DigitalOcean Droplets
context_type: brownfield
tech_stack:
  language: Clojure
  framework: Biff v1.9.0
  runtime: JVM (Clojure 1.12)
  database: XTDB 1.24
---

## Recommendation

**Stay on Mikrus 3.5 for V1.1. Migrate when you hit known triggers.**

Mikrus 3.5 is already paid ($49/year, $0 marginal cost for V1.1), provides sufficient resources (4GB RAM = 4× Biff's 1GB minimum), supports Docker (GA), and has proven capable of running V1 in production. The platform scores poorly on agent-friendly criteria (2/5: no official CLI, Polish-only docs, no MCP server, manual deploy workflow), but the $252–$516/year cost difference vs. managed alternatives buys operational tolerance for those gaps at MVP scale.

**Migration triggers** (when to move): IPv4 port exhaustion (>3 forwarded services), need for managed Postgres with automatic backups, agent-driven CI/CD requirements (git-push deploy, atomic rollback), English-first documentation for agent discoverability, or monthly revenue exceeding $500 (validating product-market fit justifies infrastructure investment).

## Platform Comparison

Six platforms researched; two dropped by hard filters (no JVM support), four scored against agent-friendly criteria, one assessed as current brownfield host.

### Dropped Platforms

**Cloudflare Workers/Pages** — No native JVM runtime (Workers support JS/Python/Rust only). Workaround exists via Cloudflare Containers (GA April 2026) but requires Docker + fundamentally different deployment model (stateful containers vs. edge functions). Unsuitable for Biff's persistent-process architecture.

**Vercel** — Architectural mismatch. Serverless-only (no persistent processes), no JVM runtime, no WebSocket server support, no Docker deployment. Optimized for Node.js/frontend workloads, not backend frameworks expecting long-lived connections.

### Scored Platforms

| Platform | CLI-first | Managed/Serverless | Agent-docs | Stable Deploy | MCP | Total | 12-mo Cost |
|---|---|---|---|---|---|---|---|
| **Mikrus 3.5** | Partial | Fail | Fail | Partial | Fail | **2/5** | **$0 marginal** |
| DigitalOcean | Pass | Partial | Pass | Pass | Pass | **4.5/5** | $252 |
| Fly.io | Pass | Partial | Partial | Pass | Pass | **4/5** | $516 |
| Railway | Pass | Partial | Pass | Partial | Pass | **4.5/5** | $120-300 |
| Render | Pass | Pass | Pass | Pass | Fail | **4/5** | $384-600 |

### Shortlisted Platforms

#### 1. Mikrus 3.5 (Current Host, Recommended for V1.1)

**Why it stays competitive:** $0 marginal cost (already paid), 4GB RAM exceeds Biff requirements by 4×, Docker support is GA, persistent processes + WebSockets work without restriction, proven operational history with V1.

**Why agent-friendliness is low:** No official CLI (community Rust/Deno/Go CLIs exist but require manual setup), Polish-language HTML docs (no `llms.txt`, no markdown mirror), no MCP server, deploy workflow is manual SSH + `git pull` + `docker-compose restart` (no atomic rollback). IPv4 port forwarding capped at 4 total (1 SSH + 3 services) — nginx reverse proxy required for multi-service setups.

**When it becomes the wrong choice:** IPv4 port limit hits (>3 forwarded services), agent requires git-push deploy automation, need for managed Postgres with automated backups (Mikrus offers shared DB with unknown limits, or DIY Docker), English-first docs become load-bearing for agent discoverability.

#### 2. DigitalOcean Droplets (Runner-Up)

**Why it scored second:** Biff's native deployment path (`server-setup.sh`, `clj -M:dev deploy`), official MCP server (GA), `doctl` CLI covers all operations, `llms.txt` + markdown docs, user has VPS familiarity. Managed Postgres available ($15/mo). Cost: $6/mo Droplet + $15/mo Postgres = $21/mo ($252/year).

**Why it didn't win:** $252/year is $252 *more* than Mikrus for V1.1 (Mikrus is already paid). Droplets are VPS (you manage OS updates, firewall, systemd), not PaaS — operational burden similar to Mikrus but with better tooling. Biff's git-based deploy (`rsync` uberjar) is fragile vs. atomic container deploys (Fly.io, Railway). No automatic rollback on health check failure.

#### 3. Fly.io (Third Place)

**Why it's a strong alternative:** Docker-native, persistent processes + WebSockets GA, automatic rollback on failed health checks, official MCP server. JVM apps proven in production. Cost: ~$5/mo compute + $38/mo managed Postgres (HA included) = $43/mo ($516/year).

**Why it's expensive for MVP:** Postgres at $38/mo is 2.5× DigitalOcean's $15/mo (though HA is included). No `llms.txt` (docs in GitHub markdown). Memory tuning required for JVM on small instances (community reports OOMs even on 2GB shared-cpu-4x — must set `-Xmx` below instance RAM).

## Anti-Bias Cross-Check: Mikrus 3.5

### Devil's Advocate — Weaknesses

1. **Agent operability gap** — No official CLI, Polish-only docs, no MCP server. Every agent query requires community CLI invocation + text parsing or web search for English context. Compare to DigitalOcean/Fly.io/Railway: official CLIs, `llms.txt`, MCP servers.

2. **Manual deploy workflow** — Git pull + Docker restart is not atomic. If the new container fails to start, the app is down until manual SSH intervention. No health-check-driven rollback (Fly.io has this). Agent can't "one-command deploy" like `wrangler deploy` or `fly deploy`.

3. **IPv4 port wall at 4 services** — SSH + app + metrics + admin panel = port exhaustion. Nginx reverse proxy required for multi-service setups. IPv6 works but complicates external integrations (many SaaS APIs IPv4-only).

4. **DIY Postgres = manual backups** — Mikrus offers "shared database" with undefined limits, or you run Postgres in Docker yourself. No automatic backups, no point-in-time recovery. Backup automation = cron + rsync to "Strych" storage (manual setup).

5. **LXC shared kernel + JVM** — JVM can't detect container memory limits in LXC. Must manually set `-Xmx` to avoid OOM kills. Community CLI docs mention this, but it's a trap for first-time JVM-on-Mikrus deployers.

### Pre-Mortem — How This Could Fail

The team kept V1.1 on Mikrus 3.5 to avoid the $252/year DigitalOcean cost. Six months later, staying was a mistake.

V1.1 added a metrics endpoint (Prometheus exporter), an admin panel for CSV import debugging, and background job processing for AI summary generation. The app consumed ports 8080 (main), 9090 (metrics), 8081 (admin), leaving zero IPv4 ports for the background worker's health check. The team set up an nginx reverse proxy on IPv6, but the monitoring SaaS (IPv4-only) couldn't reach the metrics endpoint — alerts broke, and a 3-hour outage went undetected.

The agent attempted a deploy during off-hours. `git pull` succeeded, but `docker-compose restart` failed mid-swap — the new image had a typo in `config.env`. The container exited, the old container was already stopped, and the app was down. The agent had no rollback command (Mikrus has no `mikrus rollback`). The on-call developer SSH'd in at 2 AM to manually restore the old image from `docker images` output.

Postgres ran in a Docker container (DIY, no managed option). The backup cron job had a path typo — backups silently failed for two months. A disk-full event corrupted XTDB mid-write. The team discovered the backup gap during restoration. They rebuilt the database from user-exported CSVs, losing acceptance counters and generation metadata.

The $252/year saved on DigitalOcean was spent 10× over in lost evenings debugging port exhaustion, manual rollbacks, and backup gaps.

### Unknown Unknowns

1. **Mikrus shared-DB limits are undocumented** — The platform offers "shared PostgreSQL" for 2.x/3.x plans, but connection limits, storage caps, and backup retention are not published. You discover the limits by hitting them in production.

2. **Community CLIs are maintained by volunteers** — The Rust CLI ([pwittchen/mikrus-cli](https://github.com/pwittchen/mikrus-cli)) is most feature-complete but has no SLA, no commercial support, and could be abandoned. If the Mikrus API changes, the CLI breaks until a volunteer patches it.

3. **LXC kernel sharing means noisy-neighbor risk** — Shared kernel across all Mikrus VPS instances. If another customer runs a CPU-intensive process, your JVM's GC pauses may spike. Mikrus doesn't publish isolation guarantees or noisy-neighbor mitigation policies.

4. **No published SLA or uptime guarantee** — Support is Facebook group + Discord. If Mikrus has a multi-hour outage, there's no compensation, no status page, and no escalation path beyond posting in the community.

5. **Agent can't self-heal without SSH access** — If you rotate SSH keys in the Mikrus panel and forget to update the agent's config, the agent is locked out. No recovery path except manual panel login to re-add the key. Compare to MCP servers with OAuth (Cloudflare, DigitalOcean, Railway): token refresh is automatic.

## Operational Story

How Mikrus 3.5 operates day-to-day for V1.1, and what changes when you migrate to DigitalOcean or Fly.io.

- **Preview deploys**: None on Mikrus (DIY: separate VPS or Docker container on same host). DigitalOcean requires separate $6/mo Droplet per environment. Fly.io/Railway: easy multi-app setup, preview environments ~$5-10/mo each.

- **Secrets**: Mikrus = SSH + `.env` file on disk (600 permissions). Rotation: manual edit + container restart. DigitalOcean: `doctl` can set env vars via API; secrets live in Droplet filesystem same as Mikrus. Fly.io/Railway/Render: platform-managed secret vaults with CLI/API rotation.

- **Rollback**: Mikrus = manual (`docker images`, `docker-compose up` with old image tag, or `git checkout <old-sha>` + restart). ~5-10 minutes if scripted, longer if debugging. DigitalOcean Droplets = same (git-based, no atomic rollback). Fly.io = `fly releases --image` + `fly deploy -i <sha>` (~30s). Railway/Render = one-click or API call to prior deployment (~1-2 min).

- **Approval**: Mikrus = all actions require SSH (agent or human). No panel-only gates. DigitalOcean Droplets = same (SSH-based). Fly.io/Railway/Render = destructive actions (delete app, rotate production secrets) can be restricted to panel-only via RBAC (not available on Hobby tiers).

- **Logs**: Mikrus = `mikrus logs` (community CLI, last N lines) or SSH to `/var/log/docker/`. No centralized aggregation. DigitalOcean Droplets = `doctl apps logs` or SSH. Fly.io = `fly logs --follow` with filtering. Railway/Render = web panel + CLI, structured JSON logs, retention varies by tier.

## Risk Register

| Risk | Source | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| IPv4 port exhaustion (>3 services) | Devil's advocate | Medium | High (blocks multi-service architecture) | Use nginx reverse proxy on IPv6; plan migration to Fly.io/Railway when service count exceeds 3 |
| Manual deploy failure leaves app down | Pre-mortem | Medium | High (outage until manual SSH recovery) | Script rollback (`docker images` → `docker-compose up` old tag); test rollback procedure quarterly |
| Backup automation silently fails | Pre-mortem | High | Critical (data loss on XTDB corruption) | Weekly manual restore test; monitor backup file timestamps; consider managed Postgres on DO/Fly.io |
| Community CLI abandonment | Unknown unknowns | Low | Medium (agent loses operational access) | Document fallback to raw `curl` against Mikrus API; bookmark API docs |
| LXC noisy-neighbor CPU spikes | Unknown unknowns | Low | Medium (GC pauses, user-visible latency) | Monitor JVM GC metrics; migrate to dedicated VM (DO Droplet, Fly.io) if P95 latency >500ms |
| Agent lockout on SSH key rotation | Unknown unknowns | Low | Medium (agent can't deploy until manual re-key) | Store SSH private key in version control (encrypted) or secret manager; document re-key procedure |
| Shared-DB connection limit hit | Unknown unknowns | Medium | High (app crashes on DB pool exhaustion) | Run Postgres in Docker with known limits (max_connections=100); migrate to managed Postgres if load grows |
| Polish-only docs block agent troubleshooting | Devil's advocate | Medium | Medium (agent relies on web search vs. platform docs) | Maintain English runbook in `docs/mikrus-operations.md`; link to community CLI README |
| No SLA = multi-hour outage no recourse | Unknown unknowns | Low | Medium (revenue impact if monetizing) | Set migration trigger: if monthly revenue >$500, move to platform with SLA (DO, Fly.io Pro, Render Team) |

## Getting Started (V1.1 on Mikrus 3.5)

Your app is already running on Mikrus. These steps optimize the current setup for agent-driven maintenance:

1. **Install community CLI** (choose Rust variant for best feature coverage):
   ```bash
   # On your local machine or CI
   cargo install mikrus-cli
   # Or download binary from https://github.com/pwittchen/mikrus-cli/releases
   
   # Configure with Mikrus API key + server ID (from panel at mikr.us)
   mikrus config set --api-key YOUR_API_KEY --server-id YOUR_SERVER_ID
   
   # Verify
   mikrus status
   ```

2. **Script atomic-ish rollback**:
   ```bash
   # Save as scripts/rollback.sh
   #!/bin/bash
   set -e
   PREV_IMAGE=$(docker images 10x-apiary --format "{{.Tag}}" | sed -n '2p')
   docker-compose -f /opt/apriary/docker-compose.yml down
   docker-compose -f /opt/apriary/docker-compose.yml up -d --no-build 10x-apiary:${PREV_IMAGE}
   docker ps | grep apriary
   ```

3. **Automate backup verification**:
   ```bash
   # Save as scripts/verify-backup.sh
   #!/bin/bash
   LATEST_BACKUP=$(ls -t /opt/apriary/backups/storage-*.tar.gz | head -1)
   BACKUP_AGE=$(( $(date +%s) - $(stat -c %Y "$LATEST_BACKUP") ))
   if [ $BACKUP_AGE -gt 691200 ]; then  # 8 days
     echo "ERROR: Backup older than 8 days" >&2
     exit 1
   fi
   echo "Backup OK: $LATEST_BACKUP"
   ```
   
   Add to cron: `0 6 * * * /opt/apriary/scripts/verify-backup.sh || mail -s "Backup verification failed" you@example.com`

4. **Document manual operations in English**:
   - Create `docs/mikrus-operations.md` with SSH troubleshooting, port forwarding setup, JVM `-Xmx` tuning for 4GB instance
   - Link to community CLI README: https://github.com/pwittchen/mikrus-cli
   - Bookmark Mikrus wiki (Polish): https://wiki.mikr.us/

5. **Monitor migration triggers**:
   - Track number of services (current: 1 app; threshold: 3 services = port exhaustion)
   - Track monthly revenue (threshold: $500/mo = migrate to managed platform)
   - Track deploy failure rate (threshold: >1 manual SSH recovery per month = need atomic rollback)

## Migration Path (When Triggers Hit)

When you hit a migration trigger, follow this sequence:

**Phase 1: Pre-migration (staging environment)**
1. Provision staging on target platform (DigitalOcean $6/mo Droplet or Fly.io shared-cpu-1x)
2. Deploy current V1.1 codebase via new platform's tooling (Biff `server-setup.sh` for DO, Dockerfile for Fly.io)
3. Run parallel deployment for 7 days (Mikrus = prod, new platform = staging)
4. Verify: deploy workflow, rollback procedure, log access, backup automation

**Phase 2: Migration**
1. Enable maintenance mode on Mikrus (show "upgrading" banner)
2. Export XTDB data (`tar -czf xtdb-export.tar.gz /opt/apriary/storage/xtdb`)
3. Deploy to new platform with restored data
4. Update DNS (low TTL: 300s)
5. Monitor both platforms for 24-48 hours

**Phase 3: Post-migration**
1. Verify production traffic on new platform
2. Keep Mikrus running 7 days as fallback (DNS rollback if critical issues)
3. Cancel Mikrus renewal (or repurpose for dev/staging — yearly billing already paid)

**Recommended migration target:** DigitalOcean Droplets if you value Biff-native tooling + VPS familiarity + cost ($21/mo with managed Postgres). Fly.io if you need atomic rollbacks + agent-first experience ($43/mo with HA Postgres).

## Out of Scope

The following were not evaluated in this research:
- Docker image optimization (multi-stage builds, layer caching) — covered in existing `docker-deployment-guide.md`
- CI/CD pipeline setup (GitHub Actions, deploy hooks) — deferred until migration trigger hits
- Production-scale architecture (multi-region, HA, DR) — out of scope for MVP
- Mikrus 1.0/2.1 plans — assessed 3.5 only (user's current plan)
