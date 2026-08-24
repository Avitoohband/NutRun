# Future product backlog notes

Captured ideas not yet scheduled as engineering tasks.

## First-run and replayable tutorial (deferred)

**Intent:** Guide new users through core flows on first login, with a Settings option to run the tutorial again.

**Suggested scope when scheduled:**

- First launch after onboarding: optional full-screen or stepped tutorial covering Today, Training, Nutrition, Walk, and Progress.
- Settings → Help → **Run tutorial again** (same flow, skippable).
- Persist `tutorialCompletedAt` / `tutorialVersion` on session or profile so we can show updated tours after major UX changes.
- Use existing `NutRunScreen` / semantics patterns from Task 15; no blocking of app use if user skips.
- Tests: first-run flag, settings replay, skip/complete paths.

**Depends on:** Tasks 16–19 stabilizing primary workflows (recommended after Task 19 polish).

---

## Real AdMob ads and Google Play release (readiness)

See `docs/plans/04-ux-ui-improvement-backlog.md` Task 19 and release gates below.

**Current state:** Banner placement uses `BuildConfig.ADMOB_BANNER_ID` with test/production IDs from build config; billing/entitlement trial and subscriber paths exist.

**Before production ads:**

- [ ] Production AdMob app ID and ad unit IDs in release `build.gradle` / secrets (not test IDs).
- [ ] User Messaging Platform (UMP) / consent where required (EU, UK).
- [ ] Privacy policy URL covering ads, analytics, and health data.
- [ ] Verify ads only show for `FREE_AD_SUPPORTED` entitlement (already gated).
- [ ] No ads on onboarding, active workout, or walk recording screens.

**Before Play Store upload:**

- [ ] Task 19: `assembleRelease` with signing config, auth/onboarding polish, profile grouping.
- [ ] Privacy policy, terms, and data safety form (health, location, account).
- [ ] Content rating questionnaire.
- [ ] Store listing (screenshots, description, feature graphic).
- [ ] Internal testing track → closed testing → production.
- [ ] Backend/sync stability for production Firebase project.
- [ ] Crash-free sessions target on real devices.

**Realistic sequencing:** Start **release planning** after Task 18–19; **production ads** can ship in the same release candidate once consent and policy are in place.
