import glob
import json
import os
import shutil
from datetime import datetime

CONFIG_DIR = os.environ.get("BOOKMAP_CONFIG_DIR", r"C:\Bookmap\Config")
JAR_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "AutoPriceLevels.jar")

PRICE_LEVELS_TEXT = json.dumps({
    "isIndicatorEnabled": True,
    "isResetEnabled": True,
    "resetAtTimeNs": 0,
    "resetEveryTimeNs": 0,
    "resetType": "AT",
    "settings": {
        "Highest Price Level": {
            "color": {
                "value": -65536,
                "frgbvalue": None,
                "fvalue": None,
                "falpha": 0.0,
                "cs": None
            },
            "percent": 1.0,
            "isEnabled": True
        },
        "Middle Price Level": {
            "color": {
                "value": -256,
                "frgbvalue": None,
                "fvalue": None,
                "falpha": 0.0,
                "cs": None
            },
            "percent": 0.5,
            "isEnabled": True
        },
        "Lowest Price Level": {
            "color": {
                "value": -16711936,
                "frgbvalue": None,
                "fvalue": None,
                "falpha": 0.0,
                "cs": None
            },
            "percent": 0.0,
            "isEnabled": True
        }
    }
}, indent=2)

PRICE_LEVELS_ENTRY = {
    "version": 5,
    "text": PRICE_LEVELS_TEXT
}

AUTO_ADDON_KEY = "com.bookmap.addons.autopricelevels.AutoPriceLevels"
AUTO_ADDON_ENTRY = {
    "file": {
        "path": JAR_PATH
    },
    "entryPoint": {
        "strategyName": "Auto Price Levels",
        "className": AUTO_ADDON_KEY,
        "mode": "ATTACH",
        "simplified": False
    },
    "wasEnabled": True,
    "isDisableOnLoad": False,
    "isAutoEnable": True,
    "isAutoLoad": True,
    "isFromPluginSystem": False
}


def update_config_file(filepath: str) -> None:
    if not os.path.exists(filepath):
        print(f"Skipping (not found): {filepath}")
        return

    backup_path = f"{filepath}.backup_{datetime.now().strftime('%Y%m%d_%H%M%S')}"
    shutil.copy2(filepath, backup_path)
    print(f"Created backup: {backup_path}")

    with open(filepath, "r", encoding="utf-8") as f:
        data = json.load(f)

    settings = data.setdefault("settings", {})

    # 1. Update lastUsedChart / lastUsedChartSettings
    for key in ("lastUsedChart", "lastUsedChartSettings"):
        last_used = settings.get(key)
        if isinstance(last_used, dict):
            strat_map = last_used.setdefault("strategiesSettings", {})
            strat_map["Price Levels"] = dict(PRICE_LEVELS_ENTRY)

    # 2. Update all cached charts in charts
    charts = settings.get("charts", [])
    updated_count = 0
    for chart in charts:
        if isinstance(chart, dict):
            strat_map = chart.setdefault("strategiesSettings", {})
            strat_map["Price Levels"] = dict(PRICE_LEVELS_ENTRY)
            updated_count += 1

    # 3. Register AutoPriceLevels.jar in strategiesSettings.strategies with Auto-enable + Auto-load ON
    strat_settings = settings.setdefault("strategiesSettings", {})
    strategies_dict = strat_settings.setdefault("strategies", {})
    strategies_dict[AUTO_ADDON_KEY] = dict(AUTO_ADDON_ENTRY)

    with open(filepath, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)

    print(f"Updated {filepath}: enabled Price Levels (v5) on {updated_count} charts + lastUsedChart, and registered Auto Price Levels add-on.")


def main() -> None:
    if not os.path.isdir(CONFIG_DIR):
        print(f"Bookmap config directory not found: {CONFIG_DIR}")
        return

    config_files = sorted(glob.glob(os.path.join(CONFIG_DIR, "bookmap_config_v*.json")))
    # Update the latest bookmap_config_vXX.json (or all v22+ configs) and all user .bmw workspaces
    latest_configs = [
        p for p in config_files
        if not os.path.basename(p).endswith((".bak",))
    ]
    if latest_configs:
        # Sort numerically by version number so the newest config is updated
        def _ver(path: str) -> int:
            stem = os.path.splitext(os.path.basename(path))[0]
            digits = "".join(ch for ch in stem if ch.isdigit())
            return int(digits) if digits else 0

        latest_configs = [max(latest_configs, key=_ver)]

    bmw_files = [
        p for p in sorted(glob.glob(os.path.join(CONFIG_DIR, "*.bmw")))
        if not os.path.basename(p).upper().startswith("BOOKMAP-DATA-BETA-")
    ]

    targets = latest_configs + bmw_files
    if not targets:
        print(f"No Bookmap config or .bmw workspace files found in {CONFIG_DIR}")
        return

    for target in targets:
        update_config_file(target)


if __name__ == "__main__":
    main()
