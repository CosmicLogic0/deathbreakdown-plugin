# Death Breakdown Plugin (Paper)

The Paper version of [Death Breakdown](https://github.com/CosmicLogic0/deathbreakdown). When a player dies they get a
recap of every hit from the last 10 seconds, in chat or as a book they can click open. It works on vanilla clients.

- One jar for Paper 1.12.2, 1.16.5, 1.18.2, 1.19.2, 1.20.1, 1.21.1, 1.21.11, 26.1, 26.2 and 26.3
- `/deathbreakdown` (`/db`) reopens your last recap; `/db reload` reloads `config.yml`
- **Requires LogicLib Plugin** (CosmicLogic's shared library for Paper), installed next to it in `plugins/`

| Folder | What's in it |
|---|---|
| `src/` | The plugin |
| `core/` | Plain-Java core, shared with the mod. **The master copy lives in the mod repo**; this is a copy. |
| `ci/` | Automatic tests copied from LogicLib for Paper |

MIT license.
