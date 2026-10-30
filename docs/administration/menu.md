# Complete admin menu

Run `/mob` as OP, or grant `themob.admin.menu` plus the permissions for the intended actions. The 54-slot inventory requires no pack. Existing commands remain available.

The menu covers mobs/bosses, native and provider items, spawn schedules, dungeons, skills, all configuration files, integrations and maintenance. The full tree editor supports sections and lists, including phase and drop definitions. Text prompts expire after two minutes; abandoned drafts expire after ten minutes.

Saving checks the document, detects concurrent file changes and creates a byte-for-byte `.admin.bak`. Saving does not implicitly reload the server. Integration and skill switches apply after restart. Dangerous dungeon and maintenance actions have their own confirmation.

[Complete configuration and examples](../reference/ADMIN-MENUE.md). The detailed operator references currently use German; the public API developer guide is English.
