# Hosting Analysis for Apriary Application

## 1. Main Framework Analysis

**Biff** is the primary framework driving this application. Biff is a batteries-included web framework for Clojure designed specifically for solo developers. It combines both web framework functionality and deployment tooling in a single cohesive package.

### Operational Model

Biff operates as a **traditional server-based web application** running on the JVM:

- **Long-running process**: Unlike serverless platforms, Biff requires a persistent server process
- **Stateful architecture**: Uses XTDB for database operations, which can run in standalone mode (filesystem-based) or with a managed PostgreSQL backend
- **Monolithic deployment**: The entire application is packaged as a single uberjar
- **Resource requirements**: Minimum 1GB RAM for production deployment
- **VPS-oriented**: Designed for deployment on traditional VPS infrastructure

This operational model directly influences hosting choices - the application requires **persistent compute instances** (VPS/containers) rather than serverless or edge computing platforms. The framework's design philosophy emphasizes simplicity and self-hosting over cloud-native abstractions.

## 2. Recommended Hosting Services

### From Biff Framework Creators

**Important Note**: Biff does not have official hosting partnerships or creator-operated hosting services. The framework is intentionally platform-agnostic. However, the creator (Jacob O'Bryant) provides explicit deployment documentation and tooling for:

1. **DigitalOcean Droplets** (Primary Recommendation)
   - Biff ships with `server-setup.sh` script tested specifically on DigitalOcean Ubuntu VPS
   - Official documentation uses DigitalOcean as the reference platform
   - Built-in deployment command: `clj -M:dev deploy`

2. **Self-hosting on any Ubuntu VPS**
   - Platform-agnostic uberjar deployment via `clj -M:dev uberjar`
   - Includes Dockerfile for container-based deployment
   - Can run on any JVM-compatible infrastructure

3. **No third option from creators** - Biff philosophy is "deploy anywhere that runs a JVM"

### Mikr.us Analysis

**Mikr.us** is a Polish budget VPS provider offering LXC container-based hosting:

- **Pricing**: 35zł/year (~$8/year) for Mikrus 1.0, 75zł/year (~$18/year) for Mikrus 2.1, 130zł/year (~$32/year) for Mikrus 3.0, 197zł/year (~$49/year) for Mikrus 3.5
- **Docker Support**: **NOT available on 1.0 version**, only on 2.x and 3.x plans
- **Plans for Biff**:
  - Mikrus 2.1: 1GB RAM, 10GB storage (meets minimum)
  - Mikrus 3.0: 2GB RAM, 25GB storage (comfortable)
  - **Mikrus 3.5: 4GB RAM, 40GB storage (excellent)**
- **Technology**: LXC containers with shared kernel (not true VPS)
- **Location**: All servers in Helsinki, Finland
- **Conclusion**: Mikrus 3.0 and 3.5 provide excellent resources for Biff deployment. The 3.5 plan offers 4x the minimum RAM requirement.

## 3. Alternative Platforms

Based on Docker containerization capability and compatibility with JVM applications:

### 1. Fly.io

- **Type**: Container orchestration platform with global edge deployment
- **Pricing Model**: Usage-based billing, no free tier for new organizations (2025)
- **Base Cost**: ~$1.94/month for 256MB shared instance running continuously
- **Suitable Configuration**: 1GB instance ~$7-8/month
- **Key Features**: Global deployment, automatic HTTPS, built-in metrics

### 2. Render

- **Type**: Platform-as-a-Service with Docker support
- **Pricing Model**: Fixed monthly pricing per service
- **Base Cost**: $7/month for basic web service
- **Free Tier**: Available but with limitations (suitable for testing only)
- **Key Features**: Automatic deployments from Git, managed PostgreSQL, simple pricing

### 3. Railway

- **Type**: Modern PaaS with usage-based pricing
- **Pricing Model**: Pay-for-what-you-use starting at $5/month
- **Free Tier**: $5 one-time credit for new trials
- **Key Features**: Resource-based billing (idle services cost less), no complex configuration

## 4. Critique of Solutions

### DigitalOcean Droplets
**Score: 9/10**

#### a) Deployment Process Complexity
**Strengths**:
- Biff includes purpose-built `server-setup.sh` script
- One-command deployment: `clj -M:dev deploy`
- Automatic firewall, Nginx, and Let's Encrypt configuration
- Live development on production: `clj -M:dev prod-dev`

**Weaknesses**:
- Manual initial server setup required (SSH key configuration, DNS setup)
- No built-in staging environments - must manually provision additional droplets
- Server maintenance responsibility (OS updates, security patches)

#### b) Compatibility with Tech Stack
**Strengths**:
- Perfect match - Biff was designed with DigitalOcean in mind
- Native Ubuntu support
- Managed PostgreSQL available for XTDB backend
- 1-Click Docker app available

**Weaknesses**:
- No specific Clojure optimizations
- Generic JVM tuning required for performance

#### c) Configuration of Multiple Parallel Environments
**Strengths**:
- Can provision multiple droplets easily
- Snapshot and clone functionality
- Reserved IPs for environment stability

**Weaknesses**:
- Each environment requires separate droplet ($6+/month each)
- No built-in environment management
- Manual DNS and configuration management per environment
- Cost scales linearly with number of environments

#### d) Subscription Plans
**Pricing**: $6/month (1GB Basic), $7/month (1GB AMD Premium), $8/month (1GB Intel Premium)

**Limits**:
- 1TB bandwidth included
- Additional bandwidth: $0.01/GB
- Weekly backups: $1.20/month (20% of droplet cost)

**Commercial Use**:
- ✅ Fully commercial-use friendly
- ✅ No restrictions on monetization
- ✅ Transparent, predictable pricing
- ✅ No surprise charges with bandwidth overage alerts

**Weaknesses**:
- No free tier for production workloads
- Managed services (Postgres, Redis) add significant cost ($15/month minimum)
- Load balancers expensive ($12/month)

---

### Mikr.us
**Score: 4/10**

#### a) Deployment Process Complexity
**Strengths**:
- SSH access for full control
- Standard Linux deployment practices apply
- Can use Biff's uberjar approach

**Weaknesses**:
- No official Biff integration or documentation
- LXC containers have quirks compared to full VPS
- Limited to 7 forwarded IPv4 ports total (3 more beyond SSH)
- Manual setup of reverse proxy required for IPv6-only services
- Yearly payment only - no monthly testing option (except 7zł one-month trial)

#### b) Compatibility with Tech Stack
**Strengths**:
- Docker support on 2.x/3.x plans
- Shared PostgreSQL database included
- IPv6 support for unlimited services
- **Mikrus 3.5 with 4GB RAM exceeds Biff requirements by 4x**
- Sufficient storage (40GB on 3.5, 25GB on 3.0)

**Weaknesses**:
- Shared kernel LXC may have performance implications for JVM (vs true VPS)
- No managed database options - only shared databases
- Game servers banned (potential restriction on websocket-heavy apps)
- Network architecture requires careful planning (IPv4 port limitations)
- Lower tier plans (1.0, 2.1) have insufficient RAM

#### c) Configuration of Multiple Parallel Environments
**Strengths**:
- Very affordable for multiple environments (65zł/year per environment)
- Can run dev/staging/prod for ~$42/year total

**Weaknesses**:
- Each environment needs separate VPS
- No cloning or snapshot features
- Manual coordination of port assignments
- Shared databases complicate environment isolation
- IPv6 complexity for service discovery between environments

#### d) Subscription Plans
**Pricing**:
- Mikrus 1.0: 35zł/year (~$8/year, **no Docker**)
- Mikrus 2.1: 75zł/year (~$18/year, 1GB RAM, 10GB storage)
- Mikrus 3.0: 130zł/year (~$32/year, 2GB RAM, 25GB storage)
- Mikrus 3.5: 197zł/year (~$49/year, 4GB RAM, 40GB storage)

**Limits**:
- 1Gbps shared network
- 7 total IPv4 forwarded ports
- Unlimited IPv6 ports
- Yearly billing only
- Helsinki datacenter only (high latency for non-European users)

**Commercial Use**:
- ✅ Commercial use allowed
- ❌ Game and voice servers explicitly prohibited
- ❌ Unclear ToS regarding traffic limits and "fair use"
- ⚠️ No SLA guarantees
- ⚠️ Small provider - business continuity concerns
- ⚠️ Yearly payment lock-in problematic for growing startups

---

### Fly.io
**Score: 7/10**

#### a) Deployment Process Complexity
**Strengths**:
- Simple `fly launch` initialization
- Automatic Dockerfile detection
- Built-in secrets management
- Zero-downtime deployments
- Health check monitoring

**Weaknesses**:
- Requires learning Fly-specific configuration (`fly.toml`)
- No official Biff integration - manual Dockerfile setup needed
- Multiple moving parts (apps, machines, volumes, regions)
- Learning curve for multi-region concepts (unnecessary for MVP)

#### b) Compatibility with Tech Stack
**Strengths**:
- Excellent Docker support
- Can run any JVM application
- Persistent volumes for XTDB filesystem storage
- Global Anycast networking
- Built-in PostgreSQL (Fly Postgres)

**Weaknesses**:
- Over-engineered for a simple Biff app
- Fly Postgres pricing starts at ~$2/month but production HA setup $82-164/month
- No specific Clojure/JVM optimizations
- Health checks may need tuning for JVM startup times

#### c) Configuration of Multiple Parallel Environments
**Strengths**:
- Easy to spin up multiple apps
- Built-in PR preview environments possible with GitHub Actions
- Separate apps for dev/staging/prod
- Volume snapshots for environment cloning

**Weaknesses**:
- Each environment is a separate app (costs add up)
- No built-in environment promotion workflow
- Database cloning requires manual steps
- Secrets management per-app can become complex

#### d) Subscription Plans
**Pricing**:
- Usage-based: ~$1.94/month for 256MB instance (insufficient)
- Realistic 1GB instance: ~$7-8/month
- Persistent volumes: $0.15/GB/month
- Outbound bandwidth: $0.02/GB
- **No free tier** (eliminated in 2025)

**Limits**:
- Billed per-second (flexible but requires monitoring)
- Standard support: $29/month (separate from usage)

**Commercial Use**:
- ✅ Full commercial use allowed
- ✅ Scales to production workloads
- ⚠️ Usage-based pricing can be unpredictable for beginners
- ⚠️ Costs increase with traffic (bandwidth charges)
- ❌ No truly free tier to test monetization
- ❌ Support costs extra

---

### Render
**Score: 8/10**

#### a) Deployment Process Complexity
**Strengths**:
- Dead simple: connect Git repo, Render auto-detects Dockerfile
- Zero configuration for basic deployment
- Automatic HTTPS
- Auto-deploy on git push
- Environment variables managed via UI
- Preview environments for pull requests

**Weaknesses**:
- No REPL-driven development like Biff's `prod-dev` command
- Less control than VPS
- Build times can be slow
- No built-in Biff deployment scripts

#### b) Compatibility with Tech Stack
**Strengths**:
- Excellent Docker support
- Native PostgreSQL service integration
- Persistent disks for XTDB filesystem mode
- Health checks and auto-restarts
- Horizontal scaling available

**Weaknesses**:
- Disk persistence is additional cost ($0.30/GB/month)
- PostgreSQL minimum $7/month for paid plan
- Limited JVM memory tuning options
- Container resource limits stricter than VPS

#### c) Configuration of Multiple Parallel Environments
**Strengths**:
- **Excellent**: Built-in preview environments from pull requests
- Blueprint spec for infrastructure as code
- Easy to clone services for staging
- Shared environment variable groups
- Database branching for testing

**Weaknesses**:
- Each service billed separately (staging = double cost)
- Preview environments count toward usage
- No built-in promotion workflow
- Database cloning not as simple as advertised

#### d) Subscription Plans
**Pricing**:
- Free tier: Available but suspends after 15 minutes of inactivity
- Starter Web Service: $7/month (512MB RAM - **below Biff minimum**)
- Standard Web Service: $25/month (2GB RAM - recommended for Biff)
- PostgreSQL: Free tier available, $7/month for paid
- Bandwidth: $15 per 100GB (reduced from $30 in 2024)

**Limits**:
- Free tier suspends when inactive (not suitable for production)
- Build minutes included but can run out on free tier

**Commercial Use**:
- ✅ Commercial use fully allowed on all plans
- ✅ Clear, predictable pricing
- ✅ No surprise charges
- ✅ Credits don't expire
- ⚠️ Free tier inadequate for revenue-generating app (auto-suspend)
- ⚠️ $25/month minimum for proper Biff deployment (1-2GB RAM)
- ✅ Team plan $19/user but includes features for collaboration

---

### Railway
**Score: 6/10**

#### a) Deployment Process Complexity
**Strengths**:
- Extremely simple: connect GitHub, Railway detects everything
- One-click deploy from template possible
- Automatic HTTPS
- Built-in metrics and logs
- Service mesh networking between containers

**Weaknesses**:
- No free tier makes experimentation costly
- Credit-based model requires upfront payment
- Variable monthly bills can surprise users
- No native Biff integration

#### b) Compatibility with Tech Stack
**Strengths**:
- Native Docker support
- PostgreSQL plugins available
- Environment variable management
- Volume mounts for persistent storage
- Good for monorepo setups (can deploy multiple services)

**Weaknesses**:
- Usage-based billing for idle JVM processes (JVM always consumes some memory)
- Resource limits require manual tuning
- No specific Clojure support
- Volume pricing can add up

#### c) Configuration of Multiple Parallel Environments
**Strengths**:
- Easy PR-based environments
- Can duplicate project for staging
- Shared variables across environments
- Database templates

**Weaknesses**:
- Each environment consumes credits (costs multiply)
- No built-in environment promotion
- Harder to predict costs with multiple environments
- Database replication requires manual setup

#### d) Subscription Plans
**Pricing**:
- **No free tier** (shut down August 1, 2023)
- One-time $5 trial credit for new accounts
- Hobby plan: $5/month **minimum** but usage-based
- Usage billing: CPU + memory percentage-based
- Committed spend tiers available with discounts (March 2025)

**Limits**:
- $5 trial credit insufficient for meaningful testing
- Actual monthly cost varies based on usage
- Network egress charges separate
- Volume storage at standard rates

**Commercial Use**:
- ✅ Commercial use allowed
- ⚠️ No free tier makes MVP testing expensive
- ⚠️ Variable costs hard to predict
- ⚠️ Must commit spend upfront
- ❌ Idle services still consume credits (JVM always running)
- ✅ Metal instance seat costs waived at 80% usage (March 2025 change)

## 5. Platform Scores

### DigitalOcean: 9/10

**Reasons**:
- ✅ Native Biff support and documentation
- ✅ Predictable, transparent pricing ($6-8/month)
- ✅ Full control and flexibility
- ✅ Commercial-use friendly with no restrictions
- ✅ Managed PostgreSQL available for production XTDB backend
- ✅ Can start small and scale vertically easily
- ✅ Weekly backups available
- ❌ -1 for manual environment management complexity
- ❌ -1 for server maintenance responsibility

**Recommendation**: **Best choice for production** and long-term growth. The small monthly cost ($6) is offset by complete control and Biff-native tooling.

---

### Render: 8/10

**Reasons**:
- ✅ Simplest deployment experience (Git push to deploy)
- ✅ Excellent for multiple environments (PR previews)
- ✅ Predictable pricing with no surprise charges
- ✅ Commercial-use friendly
- ✅ Managed PostgreSQL integration
- ✅ Auto-scaling capabilities
- ❌ -1 for higher minimum cost ($25/month for 2GB RAM recommended for Biff)
- ❌ -1 for less control than VPS
- ⚠️ Free tier inadequate for commercial use (auto-suspend)

**Recommendation**: **Best PaaS option** if you prefer managed infrastructure over VPS management. Trade cost for convenience.

---

### Fly.io: 7/10

**Reasons**:
- ✅ Modern platform with excellent Docker support
- ✅ Global edge deployment (if needed)
- ✅ Flexible usage-based pricing
- ✅ Strong PostgreSQL offering
- ✅ Zero-downtime deployments
- ❌ -1 for no free tier (testing costs money)
- ❌ -1 for over-complexity for simple Biff apps
- ❌ -1 for unpredictable usage-based costs
- ⚠️ Learning curve for Fly-specific concepts

**Recommendation**: **Good alternative** if you need global deployment or have experience with Fly. Overkill for basic MVP.

---

### Railway: 6/10

**Reasons**:
- ✅ Very simple deployment UX
- ✅ Usage-based billing (pay only for what you use)
- ✅ Good Docker support
- ❌ -2 for no free tier and expensive testing ($5 credit runs out fast)
- ❌ -1 for unpredictable monthly costs
- ❌ -1 for JVM idle resource consumption (always charged)
- ⚠️ Multiple environments multiply costs quickly

**Recommendation**: **Consider only if** you're already in Railway ecosystem. Otherwise, Render or DigitalOcean offer better value.

---

### Mikr.us 3.5: 7/10 (When Already Owned: 9/10)

**Reasons**:
- ✅ Excellent resources: 4GB RAM (4x Biff minimum), 40GB storage
- ✅ Extremely cost-effective ($49/year vs $72-96/year for DigitalOcean)
- ✅ Commercial use allowed
- ✅ Docker support
- ✅ Shared PostgreSQL included
- ❌ -1 for LXC shared kernel (potential JVM performance variance)
- ❌ -1 for IPv4 port limitations (7 total including SSH)
- ❌ -1 for no SLA or business continuity guarantees
- ⚠️ Helsinki-only datacenter (acceptable for EU/global audiences with CDN)

**For Already Owned Resource (+2 points → 9/10)**:
- ✅ Zero marginal cost (sunk cost already paid)
- ✅ Immediate availability
- ✅ Perfect for MVP and early commercial testing
- ✅ Can defer migration costs until actually needed
- ✅ Risk-free experimentation

**Recommendation**:
- **General**: Good budget option for early-stage projects with moderate traffic
- **If Already Owned**: **Excellent choice** for MVP through early commercial phase. Use it until you hit real limitations (SLA needs, performance issues, or geographic distribution requirements), then migrate.

---

## Impact of Owned Resources

**Critical Update**: If you already own a Mikrus 3.5 plan (4GB RAM, 40GB storage), this fundamentally changes the recommendation calculus:

### Economic Reality
- **Marginal cost of using Mikrus 3.5**: $0 (already paid)
- **Cost to add DigitalOcean**: $72/year additional
- **Cost to add Render**: $300/year additional
- **Opportunity cost**: Can use saved funds for other business needs

### Technical Viability
- 4GB RAM = 4x Biff's minimum requirement
- 40GB storage = ample for XTDB and application data
- Docker support = full compatibility with containerized deployment
- Shared PostgreSQL = can use for XTDB backend if needed

### Risk Assessment
- **Low risk for MVP phase**: Most limitations won't matter at low traffic volumes
- **Clear migration triggers**: Move when you hit SLA needs, performance bottlenecks, or require geographic distribution
- **No lock-in penalty**: Yearly billing already paid, can migrate anytime
- **Validation before spending**: Prove product-market fit before investing in premium infrastructure

## Final Recommendations

### Phase 1: MVP & Initial Launch (Current Stage)
**USE MIKRUS 3.5** - Zero additional cost, sufficient resources

**Action Items**:
1. Deploy Biff app to Mikrus 3.5 via Docker
2. Use shared PostgreSQL for XTDB backend or filesystem mode
3. Set up monitoring to track performance and resource usage
4. Configure IPv6 for main services, IPv4 port forwarding for critical endpoints
5. Implement proper backups (Mikrus has no managed backup solution)

**Migration Triggers** (when to move to paid platform):
- Consistent high CPU/memory usage (>70% sustained)
- Need for SLA guarantees or 24/7 support
- Geographic latency issues for non-EU users
- IPv4 port limitations becoming restrictive
- Revenue justifies infrastructure investment ($100+/month)

### Phase 2: Commercial MVP with Revenue
**CONTINUE WITH MIKRUS 3.5** until hitting migration triggers, OR migrate to:

**Option A: DigitalOcean $6-8/month**
- When you need Biff's native deployment scripts
- When you need managed services (PostgreSQL, Redis)
- When you need better SLA guarantees

**Option B: Render $25/month**
- When development velocity matters more than cost
- When you need automated PR preview environments
- When you want zero infrastructure management

### Phase 3: Scaling Phase
**Render** (automatic scaling) or **DigitalOcean** (vertical then horizontal scaling)

Choose based on:
- Render: If team is small and wants to focus on product
- DigitalOcean: If you have DevOps capacity and want cost control

## Phased Migration Strategy

### Cost Comparison Over 12 Months

**Scenario A: Start with Mikrus 3.5 (Already Owned)**
- Months 0-12: $0 additional
- Total Year 1: **$0** (resource already paid for)
- Migrate only when necessary

**Scenario B: Start with DigitalOcean**
- Months 0-12: $72
- Total Year 1: **$72**
- Pay from day 1, before product validation

**Scenario C: Start with Render**
- Months 0-12: $300
- Total Year 1: **$300**
- Highest cost but highest convenience

**Recommendation**: Use Mikrus 3.5 to validate your product and generate revenue **before** investing $72-300/year in infrastructure. This is financially prudent for a side project transitioning to commercial.

## Migration Checklist (When Leaving Mikrus)

When you decide to migrate from Mikrus 3.5:

1. **Pre-Migration**
   - [ ] Document current deployment process
   - [ ] Export XTDB data (full backup)
   - [ ] List all environment variables and secrets
   - [ ] Document IPv4/IPv6 port configurations
   - [ ] Test backup restoration process

2. **Migration**
   - [ ] Provision new server (DigitalOcean/Render)
   - [ ] Deploy application to new environment
   - [ ] Run parallel deployment (old + new) for validation
   - [ ] Update DNS with low TTL (5 minutes)
   - [ ] Monitor both systems for 24-48 hours

3. **Post-Migration**
   - [ ] Update DNS to point to new server
   - [ ] Monitor error rates and performance
   - [ ] Keep Mikrus running for 7 days as fallback
   - [ ] Decommission Mikrus instance
   - [ ] Document lessons learned

## Updated Summary (With Mikrus 3.5 Already Owned)

**Immediate Recommendation**: Deploy your Biff application to Mikrus 3.5 immediately. With 4GB RAM and 40GB storage already paid for, there's no financial reason to spend $72-300/year on alternative hosting until you validate product-market fit and generate revenue.

**Key Advantages**:
- $0 marginal cost vs $72-300/year for alternatives
- Resources exceed Biff requirements (4GB vs 1GB minimum)
- Full Docker support for containerized deployment
- Low-risk experimentation environment
- Can migrate later when revenue justifies premium infrastructure

**Migration Timeline**: Stay on Mikrus 3.5 until monthly revenue exceeds $500 OR you hit technical limitations (SLA needs, performance issues, geographic distribution requirements).

## Sources

- [Biff Official Website](https://biffweb.com/)
- [Biff GitHub Repository](https://github.com/jacobobryant/biff)
- [Biff Production Deployment Guide](https://biffweb.com/docs/reference/production/)
- [Biff Tutorial: Deploy to Production](https://biffweb.com/docs/tutorial/deploy/)
- [Migrating to Biff - Jacob O'Bryant](https://obryant.dev/p/blog-migrating-to-biff/)
- [DigitalOcean Droplet Pricing](https://www.digitalocean.com/pricing/droplets)
- [Mikr.us Official Site](https://mikr.us/)
- [Mikr.us 3.5 Plan](https://mikr.us/product/mikrus-3-5/)
- [Mikr.us Review - FSGeek](https://fsgeek.pl/post/recenzja-mikrus-tani-vps/)
- [Render Pricing 2025](https://render.com/pricing)
- [Fly.io Pricing 2025](https://fly.io/pricing/)
- [Railway Pricing 2025](https://railway.com/pricing)
- [PaaS Container Deployment Comparison 2024](https://alexfranz.com/posts/deploying-container-apps-2024/)
- [Railway vs Fly.io vs Render ROI Analysis](https://medium.com/ai-disruption/railway-vs-fly-io-vs-render-which-cloud-gives-you-the-best-roi-2e3305399e5b)
