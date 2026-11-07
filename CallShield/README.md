# CallShield: block unknown and spam calls (Samsung / Android 10+)

CallShield blocks every incoming call from a number that is **not saved in your
contacts**, so loan-app, debt-recovery and telemarketing calls never ring. Your
contacts always get through.

It uses Android's official **call screening** feature (the "Caller ID & spam app"
slot in Samsung settings), so:

- it works on every Samsung Galaxy running Android 10 / One UI 2 or newer, on both SIMs
- it does **not** run in the background or drain battery. Android wakes it only when a call arrives
- it doesn't need to be your default Phone app, so you keep the Samsung Phone app
- it works **100% offline**: it has no internet permission at all (any is stripped from the build, and the
  GitHub build fails if one ever appears). It needs no data, no account and no server, and your
  contacts and call history never leave the phone

## Features

| Setting | What it does |
|---|---|
| Block numbers not in my contacts | Main switch for blocking spam and loan-app calls |
| Block private / hidden numbers | Blocks "No caller ID" calls |
| Allow numbers I have called before | Lets riders, banks and shops you phoned call you back (optional, needs call log access) |
| Let repeat callers through | A second call from the same number within 5 minutes rings, for emergencies |
| Silence instead of reject | The phone stays quiet but the call still shows as missed |
| Allow list / Block list | Always allow or always block a number, or a prefix such as `0700*` |
| Blocked calls | History of blocked calls. Tap one to allow it, block it permanently or call back |

Nigerian number formats are matched together: `+234 803 123 4567`,
`2348031234567` and `08031234567` count as the same number.

## Loan-app spam pattern detection

Loan-app and debt-recovery call centres keep switching numbers, so a fixed block list
can't keep up. CallShield watches **how** unknown numbers call you and scores each call:

| Pattern | Points |
|---|---|
| Same number calling on 2+ different days this week (3+ calls) | 3 |
| 4+ calls in a day spread over 2+ hours (8+ calls: always) | 3 |
| 4+ calls in a day, close together | 2 |
| New number in the same block of 1000 as a known spammer (e.g. `0803123 4xxx`) | 3 |
| Wave of different unknown numbers (3+ in 6 h or 6+ in 24 h) | 2 |
| Has called you 3+ times this month and you never called back (call log) | 2 |
| 3 redials within 30 minutes | 1 |
| Calling before 7am or after 9pm | 1 |
| 020… call-centre landline (Lagos 0201, Abuja 0209) | 1 |

- **Normal** (default) blocks at 3 points, **Strict** at 2. Pick Strict if you're being heavily harassed.
- Once a number is caught it goes on the spam list and is **blocked immediately on every later call**,
  and its neighbouring numbers count as suspects.
- You get a notification saying why it was blocked, with a **Not spam** button for mistakes.
- **Ask me about unknown callers that ring**: when an unknown number is allowed to ring, a
  notification offers **Block as loan spam**. Tapping it hangs up the call and blocks the number for good.
- **Scan my call history**: finds numbers in the last 30 days that already behave like collectors
  (calling on several days, never called back) so they're blocked before they call again.
- **Paste list / Share list**: swap spam numbers with friends or WhatsApp groups.
- Saved contacts, your allow list and numbers you have phoned yourself are never auto-flagged.

The rules are covered by unit tests (`app/src/test/.../SpamScorerTest.kt`), and the GitHub build runs them before it builds the APK.

## Get the APK (no computer needed: use GitHub)

1. Create a free account at github.com and make a **new repository** (it can be private).
2. Upload all the files in this folder, keeping the folders as they are,
   including the hidden `.github` folder. On a phone the easiest way is to
   unzip, then use **Add file › Upload files** in the GitHub website. You can also
   use GitHub Desktop or `git push` on a computer.
3. Open the **Actions** tab. The "Build APK" job runs automatically and takes about 3–5 minutes.
4. When it shows a green tick, open the run and download **CallShield-apk**
   under *Artifacts*. Unzip it to get `app-release.apk`.

### Or with Android Studio
Open this folder in Android Studio, wait for Gradle sync, then use
**Build › Build App Bundle(s) / APK(s) › Build APK(s)**.

## Install and set up on Samsung

1. Open the APK. If asked, allow **Install unknown apps** for your browser or My Files.
   If Play Protect warns you, tap **More details › Install anyway**. This happens
   because the app is not from the Play Store.
2. Open **CallShield** and tap **Turn on protection**.
3. Allow **Contacts** (required). Also allow **Call logs** (better spam detection), **Phone**
   (lets "Block as loan spam" hang up) and **Notifications**.
4. When asked *"Set CallShield as your default caller ID & spam app?"*, tap **Set as default**.
   - No pop-up? Go to **Settings › Apps › Choose default apps › Caller ID & spam app › CallShield**.
5. The card should now say **Protection is ON**.
6. Tap **Scan my call history for spam numbers** to block collectors who have already been calling you.

**Before you rely on it,** save the numbers you can't afford to miss (bank,
children's school, hospital, family members using a new line) or add them to the
allow list. Keep *Let repeat callers through* on if you're worried about missing
an emergency call from a stranger.

## Good to know

- **WhatsApp, Telegram and other internet calls are not covered.** Android doesn't let
  any app screen them. In WhatsApp, use Settings › Privacy › Calls › *Silence unknown callers*.
- Blocked calls may still show in Samsung Phone › Recents, marked as blocked.
- Only one app at a time can hold the "Caller ID & spam app" role. If you pick
  Truecaller or another app there later, CallShield stops blocking.
- Samsung's own Phone › Settings › Block numbers › "Block unknown/private numbers" only
  blocks hidden-ID calls. CallShield also blocks visible numbers you haven't saved.
- Before Contacts access is given, CallShield lets every call through, so it never blocks your contacts by mistake.

## Project layout

```
app/src/main/java/com/callshield/ng/
  CallBlockerService.kt   decides on each incoming call (CallScreeningService)
  BlockRules.kt           the blocking rules, in order
  SpamScorer.kt           loan-app calling-pattern detector (pure Kotlin, unit-tested)
  SeenCallStore.kt        history of unknown calls the detector learns from
  SpamActionReceiver.kt   "Block as loan spam" / "Not spam" notification buttons
  NumberFormat.kt         Nigerian number normalising and number-block matching
  PhoneUtils.kt           contacts and call-log lookups
  Prefs.kt                settings and allow/block lists
  BlockedCallStore.kt     blocked-call history
  MainActivity.kt         setup screen and switches
  NumberListActivity.kt   allow / block list editor
  BlockedLogActivity.kt   blocked-call history screen
.github/workflows/build-apk.yml   builds the APK on GitHub
```

The release APK is signed with a debug key so you can install it straight away.
Before you publish it on the Play Store, sign it with your own key. You will also
need Google's approval for the call log permission, or you can remove that feature.
