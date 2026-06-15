# Event Storming Canvas — Apriary Domain Model

This directory contains the **Event Storming board artifact** for the Apriary Summary Generation domain model, created using the interactive workshop tool from:

🔗 **https://github.com/przeprogramowani/event-storming-canvas**

---

## What is this?

`board.json` is a **snapshot of a live Event Storming session** where the domain model was explored collaboratively. The board visualizes:

- **Domain Events** (orange) — things that happened (past tense)
- **Commands** (blue) — intents that trigger events
- **Actors** (yellow) — who issues commands (Beekeeper, System, OpenRouter API)
- **Hotspots** (red) — risks, problems, open questions
- **Read Models** (green) — information actors need to make decisions
- **Policies** (purple) — reactive rules ("whenever X then Y")
- **Aggregates** (beige) — entities that enforce business invariants
- **External Systems** (pink) — systems outside the domain boundary

The X-axis represents **time** — events flow left-to-right in the order they occur.

---

## How to view the board

### Option 1: Live workshop tool (recommended)

```bash
# Clone the event-storming-canvas repo
git clone https://github.com/przeprogramowani/event-storming-canvas.git
cd event-storming-canvas

# Copy this project's board.json
cp /path/to/10x-apriary-cljs/context/domain/event-storming-canvas/board.json .

# Run the server
node server.js

# Open in browser
open http://localhost:4000
```

The browser will render the board with proper colors, swimlanes, and the timeline spine. You can edit, drag, and add cards interactively.

### Option 2: Read JSON directly

`board.json` is human-readable JSON with this structure:

```json
{
  "title": "Event Storming — Apriary Summary Generation",
  "phase": "aggregates",
  "items": [
    {
      "id": "evt-1",
      "role": "event",
      "text": "CSV pasted",
      "x": 100,
      "y": 330
    }
  ]
}
```

Each item has:
- `id` — unique identifier (e.g., `evt-1`, `cmd-3`, `hot-7`)
- `role` — type of card (`event`, `command`, `actor`, `hotspot`, `readmodel`, `policy`, `aggregate`, `external`)
- `text` — the card's content
- `x`, `y` — position on the board (x = time, y = swimlane)

---

## What's modeled in this board?

The board captures the **Apriary Summary Generation process**, from CSV import through AI generation to user acceptance:

### Core Flow
1. **CSV Import** — Beekeeper pastes observations CSV → validation → generation created
2. **AI Generation** — System requests AI summaries → OpenRouter LLM generates → summaries stored
3. **User Acceptance** — Beekeeper views summaries → accepts summary → generation counters updated

### Key Hotspots (Risks)
- **HOT-3**: OpenRouter timeout (no retry logic in MVP)
- **HOT-7**: Concurrent accepts → counter overflow (no validation in single accept)
- **HOT-5**: Partial failure (some summaries ok, some failed)
- **HOT-6**: XTDB transaction fails → inconsistent counters

### Aggregates & Invariants
- **Generation** — enforces `accepted-unedited-count + accepted-edited-count ≤ generated-count`
- **Summary** — enforces content length 50-50k chars, source transition `:ai-full` → `:ai-partial` when edited

See `context/domain/02-invariant-aggregate-refactor.md` for detailed invariant analysis.

---

## Workshop phases

The board was built incrementally through standard Event Storming phases:

1. **chaotic-exploration** — divergent brainstorm of domain events (orange stickies)
2. **timeline** — order events chronologically, merge duplicates, identify gaps
3. **hotspots** — mark risks/problems with red stickies
4. **commands-actors** — add commands (blue) and actors (yellow) for each event
5. **readmodels-policies** — add read models (green) and policies (purple)
6. **aggregates** — identify aggregates (beige) that enforce business rules

The `"phase"` field in `board.json` indicates the current phase.

---

## Related artifacts

This Event Storming board complements other domain analysis documents in `context/domain/`:

- **01-domain-distillation.md** — DDD-style domain distillation (Ubiquitous Language, subdomain classification, aggregate candidates)
- **02-invariant-aggregate-refactor.md** — Plan to enforce Generation counter invariant (hotspot HOT-7)
- **03-anti-corruption-layer.md** — Plan to isolate XTDB dependency (External system EXT-1)

The board provides a **visual, collaborative view** of the domain, while the markdown docs provide detailed technical analysis.

---

## Maintenance

- **Update the board** when the domain model changes (new events, new policies, new hotspots)
- **Re-run the workshop** when introducing new features (e.g., Product tracking would add new timeline branches)
- **Keep board.json in git** — it's a living artifact that evolves with the domain understanding

To update:
1. Run the event-storming-canvas tool with this `board.json`
2. Make changes in the browser (add/edit/move cards)
3. Copy the updated `board.json` back to this directory
4. Commit the changes with a message explaining what domain insight changed

---

## Learn more

- **Event Storming method**: https://www.eventstorming.com
- **Alberto Brandolini's book**: https://leanpub.com/introducing_eventstorming
- **Tool repo**: https://github.com/przeprogramowani/event-storming-canvas
- **10xDevs 3.0 course**: https://www.10xdevs.pl/ (this is a course material)

---

**Created:** 2026-06-15  
**Last updated:** 2026-06-15  
**Workshop participants:** Claude Sonnet 4.5 (AI moderator)  
**Domain:** Apriary Summary Generation (CSV import → AI generation → user acceptance)
