# Bookmap Auto Price Levels (`AutoPriceLevels.jar`)

Custom Bookmap Layer 1 (L1) API v2 add-on (**Bookmap 8.0+ compatible**) that unlocks global **Auto enable** / **Auto load** and automatically turns on Bookmap's built-in **Price Levels** (`v1.07`) indicator on every open and newly subscribed ticker — across **TradeStation**, **dxFeed**, **Rithmic**, **BookmapData**, and **Replay**.

## Default Preset Applied to Every Ticker

| Level | Percentage | Color | Enabled |
| :--- | :--- | :--- | :--- |
| **Highest Price Level** | `100%` | Red (`#FF0000`) | Yes |
| **Middle Price Level** | `50%` | Yellow (`#FFFF00`) | Yes |
| **Lowest Price Level** | `0%` | Green (`#00FF00`) | Yes |
| **Scheduled Resets** | `AT 00:00:00` | Chart Timezone (`America/New_York`) | Yes |

---

## Quick Laptop / Travel Setup (TradeStation + Bookmap Mirror)

`AutoPriceLevels.jar` is **pre-built and ready to use** — you do **not** need Python, Git, or a Java JDK installed on your laptop.

### Step 1 — Download the Add-On to a Permanent Folder
1. Clone this repository **or** click **Code → Download ZIP** on GitHub.
2. Place `AutoPriceLevels.jar` somewhere permanent (for example `C:\Bookmap\Addons\AutoPriceLevels.jar` or `C:\Users\<You>\bookmap-auto-price-levels\AutoPriceLevels.jar` — avoid leaving it in `Downloads` where cleanup tools might delete it).

### Step 2 — Ensure Built-In `Price Levels` & Timezone Match Desktop
1. Launch **Bookmap** (and **TradeStation** / your market data connection).
2. Check **Settings → Time zone** and set it to **`America/New_York (UTC-05:00 / UTC-04:00)`** so the `00:00:00` scheduled reset matches your desktop session high/low reset.
3. Open **Settings → Manage add-ons** and confirm Bookmap's built-in **Price Levels** indicator is installed (click **Install** / **Get** if your laptop has a fresh Bookmap install).

### Step 3 — Load `AutoPriceLevels.jar` Once
1. Open **Settings → Configure add-ons** (or click the puzzle-piece icon on the toolbar).
2. Click **Add...** and select **`AutoPriceLevels.jar`**, then choose **`Auto Price Levels`**.
3. With **`Auto Price Levels`** highlighted in the left list, check **all three boxes**:
   - `[x]` **Auto Price Levels** *(left add-on list)*
   - `[x]` **Auto enable** *(bottom right)*
   - `[x]` **Auto load** *(bottom right)*
4. Save your workspace (**File → Save Workspace**).

From this point forward, every ticker you open (from TradeStation, dxFeed, or any other feed) will automatically check **Price Levels** and plot your **100% Red / 50% Yellow / 0% Green** session levels.

> **Note on Live Equities (`@DXFEED`):** When clicking `Auto Price Levels` in *Configure add-ons* on a live dxFeed stock tab, Bookmap may display *"Most of the API modules are not allowed for this instrument"* in the right-hand panel. **This is normal and can be ignored** — `Auto Price Levels` does not need raw order-book stream access; its background synchronizer remains active and automatically enables Bookmap's built-in signed **Price Levels** add-on for you.

---

## Optional: Mirror Your Full Desktop Workspace (`.bmw`)

If you also want your exact desktop heatmap, DOM/COB columns, sub-charts, and TradeStation/dxFeed layout on your laptop:
1. Copy your `.bmw` workspace file from `C:\Bookmap\Config\` on your desktop to `C:\Bookmap\Config\` on your laptop.
2. In Bookmap on your laptop, click **File → Open Workspace** and load that `.bmw` file, then verify the `Auto Price Levels` path in **Configure add-ons** points to your laptop's `AutoPriceLevels.jar` location.

---

## Optional: Offline Config Patcher (`enable_price_levels.py`)

If **Bookmap is completely closed** and you want to pre-populate `AutoPriceLevels.jar` and stamp the `Price Levels (v5)` preset across your latest `C:\Bookmap\Config\bookmap_config_v*.json` and all `.bmw` workspace files (automatically creating timestamped `.backup_*` copies first):

```powershell
powershell -ExecutionPolicy Bypass -File .\enable-price-levels.ps1
```
*(Or run `python enable_price_levels.py` directly. Set `$env:BOOKMAP_CONFIG_DIR` first if you moved Bookmap 8.0's data folder away from `C:\Bookmap\Config`.)*

---

## Rebuilding from Source (Optional)

No system JDK/`javac` is required — `build.ps1` compiles pure Java 17 bytecode directly using Bookmap's bundled Java runtime (`C:\Program Files\Bookmap\jre\bin\java.exe` + bundled ASM/Groovy libraries):

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

## Repository Contents
- `AutoPriceLevels.jar` — Pre-built Bookmap 8.0+ L1 API v2 add-on (`v3`, obfuscation-independent dynamic binding).
- `src/com/bookmap/addons/autopricelevels/AutoPriceLevels.java` — Java reference source for the add-on.
- `build.ps1` — Zero-JDK build script using Bookmap's bundled JRE.
- `enable_price_levels.py` / `enable-price-levels.ps1` — Optional offline Bookmap config & `.bmw` workspace patcher.
