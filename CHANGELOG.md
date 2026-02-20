# Changelog

## Version 1.0.0
- Fixed duplicate "Visit Casino" options appearing in market interfaces (removed redundant CasinoMarketInteractionListener, option is now added via rules.csv only)
- Fixed "Leave" option error when attempting to visit casino on markets that are too small
- Added humorous shaming messages when returning to suspended Poker and Arena games (NPCs complain about how long you made them wait)
- Rewrote main help and gacha help text to be more concise and player-friendly
- VIP daily reward and interest messages now appear on a single line
- VIP ad prefix changed from `[VIP]` to `[Tachy-Impact VIP]`
- VIP pass option text changed to "Purchase VIP Pass (can stack)" in Financial Services
- Added VIP pass purchase option to Stargem Top-up menu with proper return navigation
- VIP remaining days now displayed on the same line as balance in notifications and Top-up menu
- Made loss-less exit options consistent: changed "Wait... (Cancel)" to "Back" in the poker setup menu; kept "Tell Them to Wait (Suspend)" during gameplay to match Arena's suspend option
- Changed lossy exit option in Poker from "Abandon Game and Leave" to "Flip Table and Leave"
- Added flavor text when player chooses to flip the table (a bystander comments "Tsk, Typical Gachy Impact player")

## Version 0.1.0
- Initial Release ("Interastral Peace Casino")
