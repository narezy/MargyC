#!/usr/bin/env python3
"""Патчер MargyC: из обычного XAPK Claude собирает один подписанный APK мода.

    python3 patcher.py com.anthropic.claude_26100220.xapk -o MargyC.apk

Нужны Python 3.8+ и Java 17+. Инструменты (apktool, APKEditor, d8, uber-apk-signer,
android.jar) скачиваются сами в ~/.cache/claude-mods при первом запуске.

Шаги:
  1. антисплит: XAPK/APKS/APKM -> один APK (APKEditor);
  2. apktool d;
  3. патчи манифеста, ресурсов и smali (ниже, функции patch_*);
  4. apktool b;
  5. сборка кода мода (src/) в отдельный classesN.dex;
  6. zipalign + подпись ключом (MARGYC_KEYSTORE или keystore/narezany.jks, в git его нет).

Обфусцированные имена классов меняются от версии к версии, поэтому патчи ищут места
вставки по строкам и сигнатурам (toString data-классов, имена полей kotlinx.serialization),
а не по именам. Если что-то не нашлось, патчер падает с понятной ошибкой.
"""
import argparse
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

HERE = pathlib.Path(__file__).resolve().parent
NEW_PACKAGE = "cat.narezany.claude"
OLD_PACKAGE = "com.anthropic.claude"
APP_NAME = "MargyC"
ICON_BACKGROUND = "#ff8fd2b1"
MOD = "Lcat/narezany/mods/"

TOOLS = {
    "apktool.jar": "https://github.com/iBotPeaches/Apktool/releases/download/v2.12.1/apktool_2.12.1.jar",
    "APKEditor.jar": "https://github.com/REAndroid/APKEditor/releases/download/V1.4.5/APKEditor-1.4.5.jar",
    "uber-apk-signer.jar": "https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/uber-apk-signer-1.3.0.jar",
    "r8.jar": "https://dl.google.com/android/maven2/com/android/tools/r8/8.13.25/r8-8.13.25.jar",
}
PLATFORM_ZIP = "https://dl.google.com/android/repository/platform-35_r02.zip"

# Ключ подписи не лежит в репозитории: с ним кто угодно подпишет «обновление», которое встанет поверх MargyC.
# Свой ключ — в keystore/ (в .gitignore) или по пути из MARGYC_KEYSTORE.
KEYSTORE = pathlib.Path(os.environ.get("MARGYC_KEYSTORE", HERE / "keystore" / "narezany.jks"))
KEY_ALIAS = os.environ.get("MARGYC_KEY_ALIAS", "narezany")
KEY_PASS = os.environ.get("MARGYC_KEY_PASS", "narezany")


class PatchError(Exception):
    pass


def log(msg):
    print(msg, flush=True)


def run(cmd, **kw):
    r = subprocess.run([str(c) for c in cmd], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, **kw)
    if r.returncode != 0:
        sys.stderr.write(r.stdout)
        raise PatchError(f"команда упала: {' '.join(map(str, cmd[:4]))} ...")
    return r.stdout


# ---------------------------------------------------------------- инструменты

def tools_dir():
    d = pathlib.Path(os.environ.get("CLAUDE_MODS_TOOLS", pathlib.Path.home() / ".cache" / "claude-mods"))
    d.mkdir(parents=True, exist_ok=True)
    return d


def download(url, dest):
    log(f"  скачиваю {url.rsplit('/', 1)[-1]}")
    tmp = dest.with_suffix(dest.suffix + ".part")
    with urllib.request.urlopen(url) as r, open(tmp, "wb") as f:
        shutil.copyfileobj(r, f)
    tmp.rename(dest)


def ensure_tools():
    d = tools_dir()
    for name, url in TOOLS.items():
        if not (d / name).exists():
            download(url, d / name)
    android_jar = d / "android.jar"
    if not android_jar.exists():
        z = d / "platform.zip"
        download(PLATFORM_ZIP, z)
        with zipfile.ZipFile(z) as zf:
            entry = next(n for n in zf.namelist() if n.endswith("/android.jar"))
            android_jar.write_bytes(zf.read(entry))
        z.unlink()
    for exe in ("java", "javac", "keytool"):
        if not shutil.which(exe):
            raise PatchError(f"не найден {exe}, нужна JDK 17+")
    return d


# ---------------------------------------------------------------- антисплит

def merge_input(src, work, tools):
    """XAPK/APKS/APKM/папка со сплитами/APK -> один APK."""
    src = pathlib.Path(src)
    if src.is_file() and src.suffix == ".apk":
        return src
    splits = work / "splits"
    shutil.rmtree(splits, ignore_errors=True)
    splits.mkdir()
    if src.is_dir():
        apks = list(src.glob("*.apk"))
        for a in apks:
            shutil.copy(a, splits / a.name)
    else:
        with zipfile.ZipFile(src) as zf:
            for n in zf.namelist():
                if n.endswith(".apk") and "/" not in n.strip("/"):
                    (splits / n).write_bytes(zf.read(n))
    apks = sorted(splits.glob("*.apk"))
    if not apks:
        raise PatchError(f"в {src} нет APK")
    if len(apks) == 1:
        return apks[0]
    log(f"антисплит: {len(apks)} APK")
    merged = work / "merged.apk"
    if merged.exists():
        merged.unlink()
    run(["java", "-jar", tools / "APKEditor.jar", "m", "-i", splits, "-o", merged])
    return merged


# ---------------------------------------------------------------- поиск в smali

class Smali:
    def __init__(self, dec):
        self.dec = dec
        self.dirs = sorted(p for p in dec.iterdir() if p.is_dir() and p.name.startswith("smali"))

    def files_with(self, needle):
        """Файлы smali, где встречается строка needle."""
        if shutil.which("grep"):
            r = subprocess.run(["grep", "-rlF", "--include=*.smali", needle, *map(str, self.dirs)],
                               stdout=subprocess.PIPE, text=True)
            return sorted(pathlib.Path(p) for p in r.stdout.splitlines())
        b = needle.encode()
        return sorted(p for d in self.dirs for p in d.rglob("*.smali") if b in p.read_bytes())

    def one_with(self, needle):
        files = self.files_with(needle)
        if len(files) != 1:
            raise PatchError(f"ожидался один файл со строкой {needle!r}, найдено {len(files)}")
        return files[0]

    def file_of(self, descriptor):
        """Lfoo/Bar; -> путь к smali."""
        rel = descriptor[1:-1] + ".smali"
        for d in self.dirs:
            p = d / rel
            if p.exists():
                return p
        raise PatchError(f"нет класса {descriptor}")


def class_of(path):
    m = re.search(r"^\.class [^\n]*?(L[^;\s]+;)", path.read_text(), re.M)
    return m.group(1)


def sub_once(text, pattern, repl, what, flags=0):
    new, n = re.subn(pattern, repl, text, count=0, flags=flags)
    if n != 1:
        raise PatchError(f"{what}: ожидалось одно совпадение, найдено {n}")
    return new


# ---------------------------------------------------------------- патчи

def patch_manifest(dec):
    m = dec / "AndroidManifest.xml"
    s = m.read_text()
    s = s.replace(f'android:authorities="{OLD_PACKAGE}.', f'android:authorities="{NEW_PACKAGE}.')
    s = s.replace(f"{OLD_PACKAGE}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
                  f"{NEW_PACKAGE}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
    s = s.replace(f'android:taskAffinity="{OLD_PACKAGE}.assist"', f'android:taskAffinity="{NEW_PACKAGE}.assist"')
    activities = (
        '        <activity android:exported="false" android:label="Моды" '
        'android:name="cat.narezany.mods.ModsActivity" '
        'android:theme="@android:style/Theme.DeviceDefault.DayNight"/>\n'
        '        <activity android:exported="false" android:name="cat.narezany.mods.ThemeActivity" '
        'android:theme="@android:style/Theme.DeviceDefault.DayNight"/>\n'
        '        <activity android:exported="false" android:name="cat.narezany.mods.CrashActivity" '
        'android:theme="@android:style/Theme.DeviceDefault"/>\n'
        '        <activity android:exported="false" android:excludeFromRecents="true" '
        'android:name="cat.narezany.mods.InfoActivity" '
        'android:theme="@android:style/Theme.Translucent.NoTitleBar"/>\n'
        '        <activity android:exported="false" android:name="cat.narezany.mods.GameActivity" '
        'android:screenOrientation="unspecified" '
        'android:theme="@android:style/Theme.DeviceDefault.NoActionBar"/>\n'
        '        <receiver android:exported="true" android:label="Clawd" android:name="cat.narezany.mods.Widget">\n'
        '            <intent-filter><action android:name="android.appwidget.action.APPWIDGET_UPDATE"/></intent-filter>\n'
        '            <meta-data android:name="android.appwidget.provider" android:resource="@xml/margyc_widget_info"/>\n'
        '        </receiver>\n')
    if "cat.narezany.mods.ModsActivity" not in s:
        s = sub_once(s, r"(\n\s*</application>)", "\n" + activities.rstrip("\n") + r"\1", "манифест")
    m.write_text(s)

    y = dec / "apktool.yml"
    t = y.read_text()
    if "renameManifestPackage:" in t:
        t = re.sub(r"renameManifestPackage: .*", f"renameManifestPackage: {NEW_PACKAGE}", t)
    else:  # apktool 2.12 не пишет пустой ключ
        t = sub_once(t, r"(?m)^packageInfo:\n", f"packageInfo:\n  renameManifestPackage: {NEW_PACKAGE}\n", "apktool.yml")
    y.write_text(t)
    app = re.search(r'<application[^>]*?android:name="([^"]+)"', s).group(1)
    return app


def patch_resources(dec):
    lc = dec / "res/xml/locales_config.xml"
    s = lc.read_text()
    langs = [d.name[len("values-"):] for d in sorted((HERE / "res").glob("values-*"))
             if re.fullmatch(r"values-[a-z]{2,3}(-r[A-Z]{2})?", d.name)]  # только языки, не values-v31 и т. п.
    for lang in langs:
        if f'android:name="{lang}"' not in s:
            s = sub_once(s, r"(\n\s*</locale-config>)", f'\n    <locale android:name="{lang}" />\\1', "locales_config")
    lc.write_text(s)

    st = dec / "res/values/strings.xml"
    st.write_text(re.sub(r'<string name="app_name">[^<]*</string>',
                         f'<string name="app_name">{APP_NAME}</string>', st.read_text()))

    # фон иконки
    (dec / "res/drawable/ic_launcher_background.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" '
        'android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
        f'    <path android:fillColor="{ICON_BACKGROUND}" android:pathData="M0,0h108v108h-108z" />\n'
        '</vector>\n')

    # свои файлы мода (картинка пасхалки) -> assets/margyc/
    if (HERE / "app-assets").exists():
        (dec / "assets/margyc").mkdir(parents=True, exist_ok=True)
        for f in (HERE / "app-assets").iterdir():
            shutil.copy(f, dec / "assets/margyc" / f.name)

    # переводы (values-*) и свои ресурсы: виджет (layout, xml, drawable, color)
    for src in sorted(d for d in (HERE / "res").iterdir() if d.is_dir()):
        dst = dec / "res" / src.name
        dst.mkdir(exist_ok=True)
        for f in src.glob("*.xml"):
            shutil.copy(f, dst / f.name)

    # apktool превращает размеченные plurals (<annotation role="verb">) в простой текст с &lt;...>,
    # и в приложении теги видны буквами. Возвращаем настоящие теги во всех языках.
    for p in dec.glob("res/values*/plurals.xml"):
        s = p.read_text()
        fixed = re.sub(r"&lt;(/?annotation)\b([^>]*)>",
                       lambda m: "<" + m.group(1) + m.group(2).replace('\\"', '"') + ">", s)
        if fixed != s:
            p.write_text(fixed)

    # атрибут вырезан шринкером, без этого aapt2 не линкует ресурсы
    for f in (dec / "res").rglob("*.xml"):
        t = f.read_text(errors="ignore")
        if "glance_isTopLevelLayout" in t:
            f.write_text(re.sub(r' app:glance_isTopLevelLayout="[^"]*"', "", t))


ICONS = [  # id, фон, передний план (spark — звезда Claude, clawd — пиксельный Clawd из icons/)
    ("margyc", "#ff8fd2b1", "spark"),
    ("claude", "#ffd97757", "spark"),
    ("forest", "#ff1f5c45", "spark"),
    ("amoled", "#ff000000", "spark"),
    ("clawd", "#fffaf3e8", "clawd"),
    ("clawd_dark", "#ff151515", "clawd"),
]
MAIN_ACTIVITY = "com.anthropic.claude.mainactivity.MainActivity"


def patch_icons(dec):
    """Выбор иконки: на каждый вариант адаптивная иконка и activity-alias главного экрана с LAUNCHER.
    Включён один (по умолчанию margyc), остальные выключены; мод переключает их через PackageManager.
    У самой MainActivity LAUNCHER убирается, иначе на рабочем столе было бы две иконки."""
    drawable = dec / "res/drawable"
    anydpi = dec / "res/mipmap-anydpi-v26"
    anydpi.mkdir(parents=True, exist_ok=True)
    shutil.copy(HERE / "icons/margyc_icon_fg_clawd.xml", drawable / "margyc_icon_fg_clawd.xml")
    for icon_id, color, fg in ICONS:
        (drawable / f"margyc_icon_bg_{icon_id}.xml").write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" '
            'android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
            f'    <path android:fillColor="{color}" android:pathData="M0,0h108v108h-108z" />\n'
            '</vector>\n')
        fg_res = "@drawable/ic_launcher_foreground" if fg == "spark" else "@drawable/margyc_icon_fg_clawd"
        (anydpi / f"margyc_icon_{icon_id}.xml").write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            f'    <background android:drawable="@drawable/margyc_icon_bg_{icon_id}" />\n'
            f'    <foreground android:drawable="{fg_res}" />\n'
            f'    <monochrome android:drawable="{fg_res}" />\n'
            '</adaptive-icon>\n')

    m = dec / "AndroidManifest.xml"
    s = m.read_text()
    act = re.search(r'(<activity[^>]*android:name="' + re.escape(MAIN_ACTIVITY) + r'"[^>]*>)([\s\S]*?)(</activity>)', s)
    if not act:
        raise PatchError("иконки: нет MainActivity")
    body = act.group(2)
    launcher = re.search(r'\s*<intent-filter>\s*<action android:name="android.intent.action.MAIN"/>\s*'
                         r'<category android:name="android.intent.category.LAUNCHER"/>\s*</intent-filter>', body)
    if not launcher:
        raise PatchError("иконки: у MainActivity нет MAIN/LAUNCHER")
    shortcuts = re.search(r'\s*<meta-data android:name="android.app.shortcuts"[^>]*/>', body)
    new_body = body.replace(launcher.group(0), "")
    if shortcuts:
        new_body = new_body.replace(shortcuts.group(0), "")
    aliases = ""
    for icon_id, _, _ in ICONS:
        aliases += (f'\n        <activity-alias android:name="cat.narezany.mods.icon.{icon_id}" '
                    f'android:targetActivity="{MAIN_ACTIVITY}" android:exported="true" '
                    f'android:enabled="{"true" if icon_id == ICONS[0][0] else "false"}" '
                    f'android:icon="@mipmap/margyc_icon_{icon_id}" android:roundIcon="@mipmap/margyc_icon_{icon_id}" '
                    'android:label="@string/app_name">\n'
                    '            <intent-filter>\n'
                    '                <action android:name="android.intent.action.MAIN"/>\n'
                    '                <category android:name="android.intent.category.LAUNCHER"/>\n'
                    '            </intent-filter>\n'
                    + (f'            {shortcuts.group(0).strip()}\n' if shortcuts else "")
                    + '        </activity-alias>')
    s = s.replace(act.group(0), act.group(1) + new_body + act.group(3) + aliases, 1)
    m.write_text(s)
    log(f"  иконки: {len(ICONS)} вариантов")


def patch_install_source(sm):
    """getInstallSourceInfo("com.anthropic.claude") падает в чужом пакете."""
    n = 0
    for f in sm.files_with("getInstallSourceInfo"):
        s = f.read_text()
        new, k = re.subn(r'const-string (\w+), "' + re.escape(OLD_PACKAGE) + r'"(\s+invoke-virtual \{\w+, \1\}, '
                         r'Landroid/content/pm/PackageManager;->getInstallSourceInfo)',
                         r'const-string \1, "' + NEW_PACKAGE + r'"\2', s)
        if k:
            f.write_text(new)
            n += k
    if n == 0:
        raise PatchError("не нашёл getInstallSourceInfo с именем пакета")


def patch_crash_log(sm, app_class):
    f = sm.file_of("L" + app_class.replace(".", "/") + ";")
    s = f.read_text()
    if "CrashLog" in s:
        return
    s = sub_once(s, r"(\.method public (?:final )?onCreate\(\)V\n\s+\.locals \d+\n)",
                 r"\1\n    invoke-static/range {p0 .. p0}, " + MOD + r"CrashLog;->install(Landroid/app/Application;)V\n"
                 r"\n    invoke-static/range {p0 .. p0}, " + MOD + r"Mods;->init(Landroid/app/Application;)V\n",
                 "Application.onCreate")
    f.write_text(s)


def patch_drawer(sm):
    """Пункт «Моды» в боковом меню. Возвращает имена классов для Names.java."""
    item_file = sm.one_with('"DrawerTabConfig(tab="')
    item = class_of(item_file)
    src = item_file.read_text()
    tab_field = tab_type = None
    for name, typ in re.findall(r"^\.field public final (\w+):(L[^;]+;)", src, re.M):
        if ".class public final enum" in sm.file_of(typ).read_text()[:300]:
            tab_field, tab_type = name, typ
            break
    if not tab_field:
        raise PatchError("DrawerTabConfig: не нашёл поле вкладки")

    # 1) список вкладок: цикл копирует пункты в ArrayList, после него добавляем свой
    hits = 0
    for f in sm.files_with(f"{item}-><init>"):
        s = f.read_text()
        pat = re.compile(r"invoke-direct/range \{[^}]*\}, " + re.escape(item) + r"-><init>\([^)]*\)V"
                         r"[\s\S]{0,300}?invoke-virtual \{(\w+), \w+\}, Ljava/util/ArrayList;->add\(Ljava/lang/Object;\)Z"
                         r"\s+goto :goto_\w+\s+(:cond_\w+)\n")

        def repl(m):
            reg = m.group(1)
            return (m.group(0) + f"\n    invoke-static/range {{{reg} .. {reg}}}, {MOD}Bridge;->"
                    "addDrawerItem(Ljava/util/List;)V\n")

        new, k = pat.subn(repl, s)
        if k:
            f.write_text(new)
            hits += k
    if hits != 1:
        raise PatchError(f"список вкладок меню: найдено мест {hits}, ожидалось 1")

    # 2) ключи LazyColumn: лямбды, которые возвращают вкладку пункта
    pat = re.compile(r"check-cast (\w+), " + re.escape(item) + r"\s+iget-object \1, \1, "
                     + re.escape(f"{item}->{tab_field}:{tab_type}") + r"\s+return-object \1\n")
    hits = 0
    for f in sm.files_with(f"{item}->{tab_field}:{tab_type}"):
        s = f.read_text()
        new, k = pat.subn(lambda m: (f"invoke-static/range {{{m.group(1)} .. {m.group(1)}}}, {MOD}Bridge;->"
                                     "key(Ljava/lang/Object;)Ljava/lang/Object;\n\n"
                                     f"    move-result-object {m.group(1)}\n\n    return-object {m.group(1)}\n"), s)
        if k:
            f.write_text(new)
            hits += k
    # таких лямбд несколько (по одной на каждый список пунктов), патчим все
    if hits == 0:
        raise PatchError("ключ пункта меню не найден")

    # 3) отрисовка пункта (item, selected, onClick: Function0, modifier?, changed)
    pat = re.compile(r"(\.method public static final \w+\(" + re.escape(item)
                     + r"Z(L[^;]+;)L[^;]+;I\)V\n\s+\.locals (\d+)\n)")
    found = []
    for f in sm.files_with(f"({item}Z"):
        s = f.read_text()
        for m in pat.finditer(s):
            found.append((f, m))
    if len(found) != 1:
        raise PatchError(f"отрисовка пункта меню: найдено {len(found)}, ожидалось 1")
    f, m = found[0]
    function0 = m.group(2)
    if int(m.group(3)) < 2:
        raise PatchError("отрисовка пункта меню: мало регистров")
    hook = (
        "\n    move-object/from16 v0, p0\n\n    move-object/from16 v1, p2\n\n"
        f"    invoke-static {{v0, v1}}, {MOD}Bridge;->callback(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;\n\n"
        f"    move-result-object v1\n\n    check-cast v1, {function0}\n\n    move-object/from16 p2, v1\n\n"
        "    move/from16 v1, p1\n\n"
        f"    invoke-static {{v0, v1}}, {MOD}Bridge;->selected(Ljava/lang/Object;Z)Z\n\n"
        "    move-result v1\n\n    move/from16 p1, v1\n")
    s = f.read_text()
    f.write_text(s.replace(m.group(1), m.group(1) + hook, 1))

    unit = None
    for uf in sm.files_with('"kotlin.Unit"'):
        c = class_of(uf)
        t = uf.read_text()
        # сам Unit: toString() возвращает "kotlin.Unit" (а не сериализатор, где эта строка тоже есть)
        if (re.search(r'const-string (\w+), "kotlin\.Unit"\s+return-object \1', t)
                and re.search(r"^\.field public static final \w+:" + re.escape(c) + "$", t, re.M)):
            unit = c
    if not unit:
        raise PatchError("не нашёл kotlin.Unit")
    return {"DRAWER_ITEM": item, "FUNCTION0": function0, "UNIT": unit}


def patch_translate(sm):
    """Обычный Text(String) в Compose -> Tr.tr(), для строк с сервера."""
    f = sm.one_with(".class public final Landroidx/compose/foundation/text/modifiers/TextStringSimpleElement;")
    s = f.read_text()
    s = sub_once(s, r"(\.method public constructor <init>\(Ljava/lang/String;[^\n]*\n\s+\.locals \d+\n)",
                 r"\1\n    invoke-static/range {p1 .. p1}, " + MOD + r"Tr;->tr(Ljava/lang/String;)Ljava/lang/String;"
                 r"\n\n    move-result-object p1\n", "TextStringSimpleElement")
    f.write_text(s)


def patch_prompt(sm):
    """«Дополнить системный промпт»: текст пресета уходит в SendMessage.hidden_context (новый API, protobuf)."""
    f = sm.one_with('"type.googleapis.com/anthropic.bard.api.v1alpha.SendMessage"')
    s = f.read_text()
    # hidden_context: repeated string, Wire копирует список через immutableCopyOf("hidden_context", list)
    s = sub_once(s, r'(const-string (\w+), "hidden_context"\s+move-object(?:/from16)? (\w+), \w+\n)',
                 lambda m: (m.group(1) + f"\n    invoke-static/range {{{m.group(3)} .. {m.group(3)}}}, {MOD}Prompt;->"
                            f"hidden(Ljava/util/List;)Ljava/util/List;\n\n    move-result-object {m.group(3)}\n"),
                 "SendMessage.hidden_context")
    f.write_text(s)


def patch_fonts(sm):
    """Мод «Шрифт»: вызовы Typeface.Builder в коде приложения идут через Font (те же регистры, invoke-static
    вместо invoke-virtual), а после Builder(assets, путь) Font запоминает путь шрифта."""
    tb = "Landroid/graphics/Typeface$Builder;"
    names = {"setFontVariationSettings([Landroid/graphics/fonts/FontVariationAxis;)": "axes",
             "setWeight(I)": "weight", "setItalic(Z)": "italic", "build()": "build"}
    call = re.compile(r"invoke-virtual \{([^}]*)\}, " + re.escape(tb) + r"->(\w+)\(([^)]*)\)(L[^;]+;)")
    ctor = re.compile(r"(invoke-direct \{(\w+), \w+, (\w+)\}, " + re.escape(tb)
                      + r"-><init>\(Landroid/content/res/AssetManager;Ljava/lang/String;\)V\n)")
    calls = ctors = 0
    for f in sm.files_with(tb + "->"):
        s = f.read_text()

        def repl(m):
            nonlocal calls
            name = names.get(f"{m.group(2)}({m.group(3)})")
            if name is None:
                return m.group(0)
            calls += 1
            return f"invoke-static {{{m.group(1)}}}, {MOD}Font;->{name}({tb}{m.group(3)}){m.group(4)}"

        s = call.sub(repl, s)
        s, n = ctor.subn(lambda m: m.group(1) + f"\n    invoke-static {{{m.group(2)}, {m.group(3)}}}, {MOD}Font;->"
                         f"asset({tb}Ljava/lang/String;)V\n", s)
        ctors += n
        f.write_text(s)
    if ctors == 0 or calls == 0:
        raise PatchError(f"шрифты: Typeface.Builder(assets, путь) {ctors}, вызовов {calls}")
    # основные шрифты (Anthropic Sans / Serif с осью opsz) — из ByteBuffer, через CustomFallbackBuilder
    cfb = "Landroid/graphics/Typeface$CustomFallbackBuilder;"
    fb = re.compile(r"invoke-virtual \{([^}]*)\}, " + re.escape(cfb)
                    + r"->(setStyle\(Landroid/graphics/fonts/FontStyle;\)" + re.escape(cfb) + r"|build\(\)Landroid/graphics/Typeface;)")
    main = 0
    for f in sm.files_with("Landroid/graphics/fonts/Font$Builder;-><init>(Ljava/nio/ByteBuffer;)V"):
        s = f.read_text()
        if cfb + "->build()" not in s:
            continue
        s, n = fb.subn(lambda m: f"invoke-static {{{m.group(1)}}}, {MOD}Font;->" + (
            f"style({cfb}Landroid/graphics/fonts/FontStyle;){cfb}" if m.group(2).startswith("setStyle")
            else f"fallback({cfb})Landroid/graphics/Typeface;"), s)
        main += n
        f.write_text(s)
    if main < 2:
        raise PatchError(f"шрифты: CustomFallbackBuilder {main}")
    return ctors, calls + main


def patch_account(sm):
    """Режим стримера и свои приветствия. Account: имя и почта идут через Streamer.seen. Имена в режиме
    стримера заменяются на точки прямо в аккаунте (так они скрыты и в полях профиля), почта — только на экране (Tr).
    Приветствие главного экрана (GreetingSlot.text с сервера) — через Greeting.server в функции, которая выбирает
    слот («chat», «code», «voice_start»): дальше текст становится AnnotatedString и мимо Tr."""
    acc = Serial(sm, "com.anthropic.claude.api.account.Account")
    code = ""
    for e in ("email_address", "full_name", "display_name"):
        if e in acc.elements and acc.types[acc.elements.index(e) + 1] == "Ljava/lang/String;":
            r = acc.reg(e)
            if e == "email_address":
                code += f"invoke-static/range {{p{r} .. p{r}}}, {MOD}Streamer;->seen(Ljava/lang/String;)V\n\n    "
            else:
                hook = "fullName" if e == "full_name" else "displayName"
                code += (f"invoke-static/range {{p{r} .. p{r}}}, {MOD}Streamer;->{hook}(Ljava/lang/String;)Ljava/lang/String;\n\n"
                         f"    move-result-object p{r}\n\n    ")
    if not code:
        raise PatchError("Account: нет полей имени и почты")
    acc.hook(code)

    # запрос изменения профиля: точки вместо имени на сервер не уходят
    upd = Serial(sm, "com.anthropic.claude.api.account.UpdateAccountRequest")
    names = upd.fields("full_name", "display_name")
    t = upd.file.read_text()
    for e, restore in (("full_name", "restoreFull"), ("display_name", "restoreDisplay")):
        field = f"{upd.cls}->{names[e]}:Ljava/lang/String;"
        t, n = re.subn(r"(\n\s+)iput-object (\w+), p0, " + re.escape(field),
                       lambda m: (f"{m.group(1)}invoke-static/range {{{m.group(2)} .. {m.group(2)}}}, {MOD}Streamer;->{restore}"
                                  f"(Ljava/lang/String;)Ljava/lang/String;\n{m.group(1)}move-result-object {m.group(2)}\n"
                                  f"{m.group(1)}iput-object {m.group(2)}, p0, {field}"), t)
        if n == 0:
            raise PatchError(f"UpdateAccountRequest: не нашёл запись {e}")
    upd.file.write_text(t)

    slot = Serial(sm, "com.anthropic.claude.api.account.GreetingSlot")
    if "text" not in slot.elements or slot.types[slot.elements.index("text") + 1] != "Ljava/lang/String;":
        raise PatchError("GreetingSlot: нет text: String")
    field = f"{slot.cls}->{slot.fields('text')['text']}:Ljava/lang/String;"
    sig = "(Ljava/util/List;Ljava/lang/String;Ljava/time/ZonedDateTime;)Ljava/lang/String;"
    done = 0
    for f in sm.files_with(field):
        t = f.read_text()
        def fix(m):
            body = re.sub(r"(\n\s+)return-object (\w+)\n",
                          lambda r: (f"{r.group(1)}invoke-static {{{r.group(2)}, p1}}, {MOD}Greeting;->server"
                                     f"(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;\n"
                                     f"{r.group(1)}move-result-object {r.group(2)}\n{r.group(1)}return-object {r.group(2)}\n"),
                          m.group(2))
            return m.group(1) + body + m.group(3)
        t2, n = re.subn(r"(\.method public static final \w+" + re.escape(sig) + r"\n)((?:(?!\.end method)[\s\S])*?" + re.escape(field)
                        + r"(?:(?!\.end method)[\s\S])*?)(\.end method)", fix, t)
        if n:
            f.write_text(t2)
            done += n
    if done != 1:
        raise PatchError(f"приветствие: функций выбора слота найдено {done}, ожидалась одна")


def patch_rest_body(sm):
    """Старый движок чата (SSE_STORE, у аккаунтов без hub) шлёт ChatCompletionRequest в REST /completion, а там
    hidden_context нет. Готовый JSON тела запроса проходит через Prompt.body: тот дописывает пресет и промпт
    мемной модели стилем (personalized_styles, как стили веб-версии)."""
    f = sm.one_with('" failed to encode ("')
    s = f.read_text()
    s = sub_once(s, r"(invoke-virtual \{[^}]*\}, L[^;]+;->\w+\(J\)(L[^;]+;)\n\s+move-result-object (\w+)\n"
                    r"\s+:try_end_\w+\n\s+\.catch [^\n]+\n)(\s+invoke-direct \{\w+, \3\}, L[^;]+;-><init>\(\2\)V\n)",
                 lambda m: (m.group(1) + f"\n    invoke-static/range {{{m.group(3)} .. {m.group(3)}}}, {MOD}Prompt;->"
                            f"body(Ljava/lang/Object;)Ljava/lang/Object;\n\n    move-result-object {m.group(3)}\n\n"
                            f"    check-cast {m.group(3)}, {m.group(2)}\n" + m.group(4)),
                 "тело JSON-запроса")
    f.write_text(s)


PARAM = re.compile(r"\[*(?:L[^;]+;|[ZBSCIFJD])")


class Serial:
    """Data-класс kotlinx.serialization по его serialName. Сериализатор перечисляет элементы
    (addElement("id", ...)) по порядку и создаёт объект синтетическим конструктором
    <init>(I маска, элемент0, элемент1, ...), а тот раскладывает параметры по полям."""

    def __init__(self, sm, serial_name):
        found = []
        for f in sm.files_with(f'"{serial_name}"'):
            s = f.read_text()
            names = re.findall(r'const-string (\w+), "([^"]+)"\s+(?:const/4 \w+, 0x[01]\s+)?invoke-virtual \{\w+, \1, \w+\}, '
                               r"Lkotlinx/serialization/internal/PluginGeneratedSerialDescriptor;->\w+\(Ljava/lang/String;Z\)V", s)
            made = set(re.findall(r"invoke-direct(?:/range)? \{[^}]*\}, (L[^;]+;)-><init>\(I[^)]*\)V", s))
            if names and len(made) == 1:
                found.append(([n for _, n in names], made.pop()))
        if len(found) != 1:
            raise PatchError(f"сериализатор {serial_name}: найдено {len(found)}, ожидался один")
        self.name = serial_name
        self.elements, self.cls = found[0]
        self.file = sm.file_of(self.cls)
        m = re.search(r"\.method public synthetic constructor <init>\((I[^)]*)\)V\n\s+\.locals \d+\n([\s\S]*?)\n\.end method",
                      self.file.read_text())
        if not m:
            raise PatchError(f"{serial_name}: нет синтетического конструктора")
        self.types = PARAM.findall(m.group(1))
        if len(self.types) < len(self.elements) + 1:
            raise PatchError(f"{serial_name}: параметров меньше, чем элементов")
        self.header = m.group(0)[:m.start(2) - m.start(0)]
        self.body = m.group(2)

    def reg(self, element):
        """Регистр параметра элемента в синтетическом конструкторе: p0 — this, p1 — маска."""
        i = self.elements.index(element) + 1
        return 1 + sum(2 if t in ("J", "D") else 1 for t in self.types[:i])

    def fields(self, *elements):
        """Элемент -> поле класса: какой параметр конструктор кладёт в какое поле."""
        param = {f"p{self.reg(e)}": e for e in self.elements}
        alias, out = {}, {}
        for line in self.body.splitlines():
            line = line.strip()
            m = re.match(r"move(?:-object|-wide)?(?:/from16|/16)? (\w+), (\w+)$", line)
            if m:
                alias[m.group(1)] = alias.get(m.group(2), m.group(2))
                continue
            m = re.match(r"iput(?:-\w+)? (\w+), p0, " + re.escape(self.cls) + r"->(\w+):", line)
            if m:
                src = alias.get(m.group(1), m.group(1))
                if src in param:
                    out.setdefault(param[src], m.group(2))
                continue
            # любая другая запись в регистр (const, and-int, move-result...) — он больше не параметр
            m = re.match(r"(?!if-|invoke|goto|return|throw|check-cast|filled|fill-|packed|sparse|monitor|aput|sput|:|\.)"
                         r"[\w/-]+ (\w+)", line)
            if m:
                alias[m.group(1)] = None
        missing = [e for e in elements if e not in out]
        if missing:
            raise PatchError(f"{self.name}: не нашёл поля для {missing}")
        return {e: out[e] for e in elements}

    def hook(self, code):
        """Вставить код в начало синтетического конструктора."""
        s = self.file.read_text()
        if self.header not in s:
            raise PatchError(f"{self.name}: конструктор уже изменён")
        self.file.write_text(s.replace(self.header, self.header + "\n    " + code.rstrip() + "\n", 1))


def hook_string_ctor(sm, needle, method):
    """Единственный конструктор с одной строкой в классе со строкой needle: строка -> Fake.method(строка)."""
    f = sm.one_with(needle)
    s = f.read_text()
    ctors = [m for m in re.finditer(r"\.method public constructor <init>\(([^)]*)\)V\n\s+\.locals \d+\n", s)
             if PARAM.findall(m.group(1)).count("Ljava/lang/String;") == 1]
    if len(ctors) != 1:
        raise PatchError(f"{needle}: конструкторов со строкой {len(ctors)}, ожидался один")
    types = PARAM.findall(ctors[0].group(1))
    reg = 1 + sum(2 if t in ("J", "D") else 1 for t in types[:types.index("Ljava/lang/String;")])
    f.write_text(s.replace(ctors[0].group(0), ctors[0].group(0)
                           + f"\n    invoke-static/range {{p{reg} .. p{reg}}}, {MOD}Fake;->{method}(Ljava/lang/String;)"
                           f"Ljava/lang/String;\n\n    move-result-object p{reg}\n", 1))


def patch_model_serializers(sm):
    """Мемный id не должен уйти на сервер ни одним запросом: в сериализаторах всех JSON-классов приложения
    с полем «model» строка при записи проходит через Fake.real (старый API чата, Code, настройки...).
    Чтение поля самим приложением не трогаем: только serialize() сериализатора и write$Self класса
    (методы, принимающие SerialDescriptor)."""
    patched = 0
    for f in sm.files_with('"model"'):
        s = f.read_text()
        if "PluginGeneratedSerialDescriptor" not in s:
            continue
        m = re.search(r'const-string \w+, "(com\.anthropic\.[^"]+)"', s)
        # ModelOption сам содержит мемные копии: записанный в кэш с настоящим id, он стал бы её дублем
        if not m or m.group(1) == "com.anthropic.claude.api.model.ModelOption":
            continue
        try:
            ser = Serial(sm, m.group(1))
            if "model" not in ser.elements or ser.types[ser.elements.index("model") + 1] != "Ljava/lang/String;":
                continue
            field = ser.fields("model")["model"]
        except PatchError:
            continue
        read = re.compile(r"(iget-object (\w+), \w+, " + re.escape(ser.cls) + "->" + re.escape(field)
                          + r":Ljava/lang/String;\n)")
        hook = (lambda mm: mm.group(1) + f"\n    invoke-static/range {{{mm.group(2)} .. {mm.group(2)}}}, {MOD}Fake;->"
                f"real(Ljava/lang/String;)Ljava/lang/String;\n\n    move-result-object {mm.group(2)}\n")
        for target in dict.fromkeys([f, ser.file]):
            text = target.read_text()
            out = []
            for method in re.split(r"(?=^\.method )", text, flags=re.M):
                header = method.split("\n", 1)[0]
                if target == f and "serialize(" in header or target != f and "SerialDescriptor;" in header:
                    method, k = read.subn(hook, method)
                    patched += k
                out.append(method)
            target.write_text("".join(out))
    if patched == 0:
        raise PatchError("сериализаторы с полем model не найдены")
    return patched


def patch_models(sm):
    """Мемные модели (Fake): свои модели в меню выбора, за которыми отвечает настоящая.

    Меню показывает модели, которые есть и в ModelSelectorConfig.models (поверхность «chat»), и в
    Organization.claude_ai_bootstrap_models_config, поэтому мемные добавляются в оба списка. Сервер
    мемного id не видит: ModelId (protobuf запроса SendMessage) и ModelSelectorStateBody (сохранение
    выбора) получают id настоящей модели. Что приходит с сервера (ModelSelectorState, настройка MODEL
    беседы, модель беседы в состоянии выбора чата), превращается обратно в мемную, иначе чат после
    ответа переключится на настоящую.
    Возвращает имена полей для Names.java."""
    entry = Serial(sm, "com.anthropic.claude.api.model.ModelSelectorEntry")
    option = Serial(sm, "com.anthropic.claude.api.model.ModelOption")
    names = {
        "FAKE_ENTRY": entry.fields("id", "name", "short_name", "description", "notice", "selection_notice",
                                   "section", "disabled", "badge"),
        "FAKE_OPTION": option.fields("model", "name", "overflow", "inactive"),
    }

    # доступность моделей по тарифу: модели, которой там нет, приложение ставит тариф Pro, и на бесплатном
    # аккаунте мемная модель «недоступна». AvailableModelsConfig(models) создаётся конструктором (List, маска).
    available = Serial(sm, "com.anthropic.claude.models.organization.configtypes.AvailableModelsConfig.AvailableModel")
    names["FAKE_AVAILABLE"] = available.fields("model_id")
    f = sm.one_with('"AvailableModelsConfig(models="')
    s = f.read_text()
    s = sub_once(s, r"(\.method public synthetic constructor <init>\(Ljava/util/List;I\)V\n\s+\.locals \d+\n)",
                 r"\1\n    invoke-static/range {p1 .. p1}, " + MOD + r"Fake;->available(Ljava/util/List;)Ljava/util/List;"
                 r"\n\n    move-result-object p1\n", "AvailableModelsConfig(models)")
    f.write_text(s)

    log(f"  поле model в сериализаторах: {patch_model_serializers(sm)}")

    cfg = Serial(sm, "com.anthropic.claude.api.model.ModelSelectorConfig")
    i, m = cfg.reg("id"), cfg.reg("models")
    if m != i + 1:
        raise PatchError("ModelSelectorConfig: id и models не подряд")
    cfg.hook(f"invoke-static/range {{p{i} .. p{m}}}, {MOD}Fake;->entries(Ljava/lang/String;Ljava/util/List;)"
             f"Ljava/util/List;\n\n    move-result-object p{m}\n")

    org = Serial(sm, "com.anthropic.claude.api.account.Organization")
    r = org.reg("claude_ai_bootstrap_models_config")
    org.hook(f"invoke-static/range {{p{r} .. p{r}}}, {MOD}Fake;->options(Ljava/util/List;)Ljava/util/List;\n\n"
             f"    move-result-object p{r}\n")

    state = Serial(sm, "com.anthropic.claude.api.model.ModelSelectorState")
    r = state.reg("model")
    state.hook(f"invoke-static/range {{p{r} .. p{r}}}, {MOD}Fake;->restore(Ljava/lang/String;)Ljava/lang/String;\n\n"
               f"    move-result-object p{r}\n")

    # тело PUT model_selector_state создаёт приложение, обычным конструктором (model, thinking)
    body = Serial(sm, "com.anthropic.claude.api.bootstrap.ModelSelectorStateBody")
    if body.elements[0] != "model" or body.types[1] != "Ljava/lang/String;":
        raise PatchError("ModelSelectorStateBody: первым ожидался model: String")
    s = body.file.read_text()
    s = sub_once(s, r"(\.method public constructor <init>\(" + re.escape("".join(body.types[1:len(body.elements) + 1]))
                 + r"\)V\n\s+\.locals \d+\n)",
                 r"\1\n    invoke-static/range {p1 .. p1}, " + MOD + r"Fake;->remember(Ljava/lang/String;)Ljava/lang/String;"
                 r"\n\n    move-result-object p1\n", "ModelSelectorStateBody(model, thinking)")
    body.file.write_text(s)

    # настройка MODEL беседы, в том числе из состояния с сервера: Model(model: String)
    f = sm.one_with('"Model(model="')
    s = f.read_text()
    s = sub_once(s, r"(\.method public constructor <init>\(Ljava/lang/String;\)V\n\s+\.locals \d+\n)",
                 r"\1\n    invoke-static/range {p1 .. p1}, " + MOD + r"Fake;->restore(Ljava/lang/String;)Ljava/lang/String;"
                 r"\n\n    move-result-object p1\n", "Model(model)")
    f.write_text(s)

    # состояние выбора модели в чате: модель беседы (из хаба, REST, сессий) кладётся в него одним
    # методом o(String) { C.setValue(ModelId(normalize(id))) }, по нему подпись под полем ввода. ModelId здесь —
    # value-класс, его a(String) зовёт toString записи меню.
    m = re.search(r"iget-object (\w+), p0, " + re.escape(entry.cls) + "->" + names["FAKE_ENTRY"]["id"]
                  + r":Ljava/lang/String;\s+invoke-static \{\1\}, (L[^;]+;)->\w+\(Ljava/lang/String;\)Ljava/lang/String;",
                  entry.file.read_text())
    if not m:
        raise PatchError("ModelSelectorEntry.toString: не нашёл value-класс ModelId")
    model_id = m.group(2)
    setter = re.compile(r"(\.method public final \w+\(Ljava/lang/String;\)V\n\s+\.locals 1\n)"
                        r"\s+invoke-static \{p1\}, L[^;]+;->\w+\(Ljava/lang/String;\)Ljava/lang/String;"
                        r"\s+move-result-object p1\s+new-instance v0, " + re.escape(model_id)
                        + r"\s+invoke-direct \{v0, p1\}, " + re.escape(model_id) + r"-><init>\(Ljava/lang/String;\)V"
                        r"\s+iget-object p0, p0, L[^;]+;->\w+:(L[^;]+;)\s+invoke-virtual \{p0, v0\}, \2->setValue"
                        r"\(Ljava/lang/Object;\)V\s+return-void")
    found = []
    for f in sm.files_with(f"new-instance v0, {model_id}"):
        s = f.read_text()
        bare = re.sub(r"\n\s*\.line \d+", "", s)
        found += [(f, mm.group(1)) for mm in setter.finditer(bare)]
    if len(found) != 1:
        raise PatchError(f"установка модели беседы: найдено {len(found)}, ожидалось 1")
    f, header = found[0]
    s = f.read_text()
    if header not in s:
        raise PatchError("установка модели беседы: заголовок метода не найден")
    s = s.replace(header, header + f"\n    invoke-static/range {{p1 .. p1}}, {MOD}Fake;->restore(Ljava/lang/String;)"
                                   "Ljava/lang/String;\n\n    move-result-object p1\n", 1)
    # там же явный выбор: n(String) { S.setValue(id != null ? ModelId(id) : null) }
    picker = re.compile(r"(\.method public final \w+\(Ljava/lang/String;\)V\n\s+\.locals 1\n)"
                        r"\s+if-eqz p1, :cond_0\s+new-instance v0, " + re.escape(model_id)
                        + r"\s+invoke-direct \{v0, p1\}, " + re.escape(model_id) + r"-><init>\(Ljava/lang/String;\)V"
                        r"\s+goto :goto_0\s+:cond_0\s+const/4 v0, 0x0\s+:goto_0\s+iget-object p0, p0, L[^;]+;->\w+:(L[^;]+;)"
                        r"\s+invoke-virtual \{p0, v0\}, \2->setValue\(Ljava/lang/Object;\)V\s+return-void")
    picks = [mm.group(1) for mm in picker.finditer(re.sub(r"\n\s*\.line \d+", "", s))]
    if len(picks) != 1:
        raise PatchError(f"выбор модели в чате: найдено {len(picks)}, ожидалось 1")
    s = s.replace(picks[0], picks[0] + f"\n    invoke-static/range {{p1 .. p1}}, {MOD}Fake;->pick(Ljava/lang/String;)"
                                       "Ljava/lang/String;\n\n    move-result-object p1\n", 1)
    f.write_text(s)

    # выбор модели для нового чата и для следующего сообщения в чате: по ним мод узнаёт, что выбрана мемная
    for needle in ('"NewChatModelSelection(model="', '"ModelSelectionForNextSend(model="'):
        hook_string_ctor(sm, needle, "pick")

    # тап по модели в меню: единственное место, где ясно, что пользователь выбрал сам. Метод ищет запись меню
    # с этим id (entry.id == id && !entry.disabled) и меняет модель. Явный выбор настоящей модели сбрасывает
    # мемную, иначе её настоящая модель так и превращалась бы обратно в мемную.
    fid, fdis = names["FAKE_ENTRY"]["id"], names["FAKE_ENTRY"]["disabled"]
    tap = re.compile(r"iget-object (\w+), (\w+), " + re.escape(entry.cls) + "->" + fid + r":Ljava/lang/String;\s+"
                     r"invoke-static \{\1, (\w+)\}, L[^;]+;->\w+\(Ljava/lang/Object;Ljava/lang/Object;\)Z\s+"
                     r"move-result \w+\s+if-eqz \w+, :\w+\s+"
                     r"iget-boolean (\w+), \2, " + re.escape(entry.cls) + "->" + fdis + r":Z\s+if-nez \4, :\w+\n")
    # такая же проверка есть и в меню Code и в других местах; смена модели чата — только там, где есть
    # «когда применить» (enum AtOnce / WithNextSend)
    timing = class_of(sm.one_with('"WithNextSend"'))
    taps = []
    for f in sm.files_with(f"{entry.cls}->{fdis}:Z"):
        s = f.read_text()
        for mm in tap.finditer(s):
            start = s.rfind("\n.method ", 0, mm.start())
            end = s.find("\n.end method", mm.end())
            if timing + "->" in s[start:end]:
                taps.append((f, mm))
    if len(taps) != 1:
        raise PatchError(f"тап по модели в меню: найдено {len(taps)}, ожидалось 1")
    f, mm = taps[0]
    s = f.read_text()
    f.write_text(s[:mm.end()] + f"\n    invoke-static/range {{{mm.group(3)} .. {mm.group(3)}}}, {MOD}Fake;->"
                 "picked(Ljava/lang/String;)V\n" + s[mm.end():])
    # и сам тап в шторке «Выбрать модель»: она зовёт выбор модели интерфейса этого класса, d(id)
    pickers = [i for i in re.findall(r"^\.implements (L[^;]+;)$", f.read_text(), re.M)
               if re.search(r"^\.method public abstract d\(Ljava/lang/String;\)L[^;]+;$", sm.file_of(i).read_text(), re.M)]
    if len(pickers) != 1:
        raise PatchError(f"интерфейс выбора модели: найдено {len(pickers)}")
    site = re.compile(r"(\n\s+)(invoke-interface \{\w+, (\w+)\}, " + re.escape(pickers[0])
                      + r"->d\(Ljava/lang/String;\)L[^;]+;\n)")
    sheet = 0
    for f in sm.files_with(pickers[0] + "->d(Ljava/lang/String;)"):
        s, n = site.subn(lambda m: f"{m.group(1)}invoke-static {{{m.group(3)}}}, {MOD}Fake;->picked(Ljava/lang/String;)V\n"
                         + m.group(1) + m.group(2), f.read_text())
        sheet += n
        f.write_text(s)
    if sheet == 0:
        raise PatchError("шторка выбора модели: вызов не найден")

    # ModelId{default, identifier} в запросах нового API. ModelId(String) приложение создаёт само для отправки
    # и смены модели беседы, основной конструктор зовёт ещё и разбор ответов сервера.
    f = sm.one_with('"type.googleapis.com/anthropic.bard.api.v1alpha.ModelId"')
    s = f.read_text()
    s = sub_once(s, r"(\.method public synthetic constructor <init>\(Ljava/lang/String;\)V\n\s+\.locals \d+\n)",
                 r"\1\n    invoke-static/range {p1 .. p1}, " + MOD + r"Fake;->send(Ljava/lang/String;)Ljava/lang/String;"
                 r"\n\n    move-result-object p1\n", "ModelId(String)")
    ctors = [m for m in re.finditer(r"\.method public constructor <init>\(([^)]*)\)V\n\s+\.locals \d+\n", s)
             if "Ljava/lang/String;" in m.group(1)]
    if len(ctors) != 1:
        raise PatchError(f"ModelId: конструкторов со строкой {len(ctors)}, ожидался один")
    types = PARAM.findall(ctors[0].group(1))
    if types.count("Ljava/lang/String;") != 1:
        raise PatchError("ModelId: ожидалась одна строка (identifier)")
    reg = 1 + sum(2 if t in ("J", "D") else 1 for t in types[:types.index("Ljava/lang/String;")])
    f.write_text(s.replace(ctors[0].group(0), ctors[0].group(0)
                           + f"\n    invoke-static/range {{p{reg} .. p{reg}}}, {MOD}Fake;->real(Ljava/lang/String;)"
                           f"Ljava/lang/String;\n\n    move-result-object p{reg}\n", 1))
    return names


def patch_theme(sm):
    """Акцент и свои темы. Все цвета создаются через Color(Long) из ARGB-константы. Тональная палитра —
    класс, где в <clinit> больше сотни таких цветов (с фирменным 0xffd97757). Тёмная и светлая темы —
    два класса, которые в <clinit> читают десятки цветов этой палитры. Возвращает палитры тем для мода."""
    pal = None
    for f in sm.files_with("0xffd97757L"):
        s = f.read_text()
        clinit = re.search(r"\.method static constructor <clinit>\(\)V\n[\s\S]*?\.end method", s)
        if not clinit:
            continue
        calls = re.findall(r"const-wide \w+, (0x[0-9a-f]+)L\s+invoke-static \{\w+, \w+\}, (L[^;]+;->\w+\(J\)J)", clinit.group(0))
        if len(calls) >= 100 and (pal is None or len(calls) > len(pal[2])):
            pal = (f, clinit.group(0), calls)
    if not pal:
        raise PatchError("не нашёл тональную палитру")
    pal_file, pal_clinit, calls = pal
    pal_cls = class_of(pal_file)
    color_fn = max(set(fn for _, fn in calls), key=lambda fn: sum(1 for _, x in calls if x == fn))

    # поле палитры -> цвет: const-wide, Color(Long), ..., sput-wide
    colors, last = {}, None
    for line in pal_clinit.splitlines():
        line = line.strip()
        m = re.match(r"const-wide \w+, (0x[0-9a-f]+)L$", line)
        if m:
            last = int(m.group(1), 16) & 0xFFFFFFFF
        m = re.match(r"sput-wide \w+, " + re.escape(pal_cls) + r"->(\w+):J$", line)
        if m and last is not None:
            colors[m.group(1)] = last
            last = None

    # схемы тем: классы из одного <clinit>, читающие из палитры хотя бы 20 раз
    # (базовые цвета и доп. цвета для каждой из тем: в этой версии ctx/ko4 — светлая, btx/jo4 — тёмная)
    read = re.compile(r"sget-wide (v(\d+)), " + re.escape(pal_cls) + r"->(\w+):J\n")
    schemes, others = [], []
    for f in sm.files_with(f"{pal_cls}->"):
        if f == pal_file:
            continue
        s = f.read_text()
        fields = [m.group(3) for m in read.finditer(s)]
        if len(re.findall(r"^\.method ", s, re.M)) == 1 and len(fields) >= 20:
            schemes.append((f, fields))
        else:
            others.append(f)

    def darkness(fields):  # доля тёмных среди прочитанных серых
        grays = [colors[x] for x in set(fields) if x in colors
                 and max(colors[x] >> 16 & 255, colors[x] >> 8 & 255, colors[x] & 255)
                 - min(colors[x] >> 16 & 255, colors[x] >> 8 & 255, colors[x] & 255) < 12]
        return sum(1 for g in grays if (g & 255) < 0x60) / max(1, len(grays))

    if len(schemes) < 2 or len(schemes) % 2:
        raise PatchError(f"схемы тем: найдено {len(schemes)}, ожидалось чётное число")
    schemes.sort(key=lambda sc: -darkness(sc[1]))
    half = len(schemes) // 2
    kinds = {sm_f: ("dark" if i < half else "light") for i, (sm_f, _) in enumerate(schemes)}
    kind_of_cls = {class_of(f): k for f, k in kinds.items()}

    def hook(text, kind):
        return read.sub(lambda m: (m.group(0) + f"\n    invoke-static/range {{{m.group(1)} .. v{int(m.group(2)) + 1}}}, "
                                   f"{MOD}Theme;->{kind}(J)J\n\n    move-result-wide {m.group(1)}\n"), text)

    result = {"DARK_PALETTE": [], "LIGHT_PALETTE": []}

    def remember(kind, fields):
        lst = result["DARK_PALETTE" if kind == "dark" else "LIGHT_PALETTE"]
        for x in fields:
            if x in colors and colors[x] not in lst:
                lst.append(colors[x])

    for f, fields in schemes:
        f.write_text(hook(f.read_text(), kinds[f]))
        remember(kinds[f], fields)

    # сборщик тем (ae9): в <clinit> собирает объект каждой темы из схем и палитры, между sput-object.
    # Чтения палитры относятся к той теме, схемы которой читаются в том же куске.
    combined = 0
    for f in others:
        s = f.read_text()
        clinit = re.search(r"\.method static constructor <clinit>\(\)V\n[\s\S]*?\.end method", s)
        if not clinit or not read.search(clinit.group(0)):
            continue
        parts = re.split(r"(\n\s*sput-object [^\n]*\n)", clinit.group(0))
        out = []
        for part in parts:
            refs = {kind_of_cls[c] for c in re.findall(r"sget-wide \w+, (L[^;]+;)->", part) if c in kind_of_cls}
            if len(refs) == 1 and read.search(part):
                kind = refs.pop()
                remember(kind, [m.group(3) for m in read.finditer(part)])
                part = hook(part, kind)
                combined += 1
            out.append(part)
        f.write_text(s.replace(clinit.group(0), "".join(out)))
    if combined == 0:
        raise PatchError("не нашёл сборщик тем")

    # сама Color(Long): акцент для всех цветов приложения
    cls, name = color_fn.split("->")
    cf = sm.file_of(cls)
    t = cf.read_text()
    t = sub_once(t, r"(\.method public static (?:final )?" + re.escape(name) + r"\n\s+\.locals \d+\n)",
                 r"\1\n    invoke-static/range {p0 .. p1}, " + MOD + r"Theme;->color(J)J\n\n    move-result-wide p0\n",
                 "Color(Long)")
    cf.write_text(t)
    return result


def find_clawd(sm):
    """Для мода Clawd: ComposeView, его setContent(Function2) и Compose-функция Clawd приложения
    (с анимацией «clawd-loop»). Не нашлось — мод рисует своего Clawd, сборка не падает."""
    names = {"COMPOSE_VIEW": "", "FUNCTION2": "", "CLAWD": "", "CLAWD_METHOD": "", "MODIFIER": "",
             "SEMANTICS_DELEGATE": "", "SEMANTICS_STALE": "", "SEMANTICS_NODES": ""}
    try:
        # делегат специальных возможностей AndroidComposeView: геттер карты узлов semantics
        # nodes() { if (stale) { stale = false; map = getAllUncovered...(view.getSemanticsOwner(), ...) } return map }
        getter = re.compile(r"\.method public final (\w+)\(\)L[^;]+;\n\s+\.locals \d+\n\s+iget-boolean (\w+), p0, (L[^;]+;)->(\w+):Z"
                            r"\s+if-eqz \2, :cond_\w+\s+const/4 \2, 0x0\s+iput-boolean \2, p0, \3->\4:Z"
                            r"\s+iget-object (\w+), p0, \3->\w+:Landroidx/compose/ui/platform/AndroidComposeView;"
                            r"\s+invoke-virtual \{\5\}, Landroidx/compose/ui/platform/AndroidComposeView;->getSemanticsOwner\(\)")
        found = []
        for f in sm.files_with("Landroidx/compose/ui/platform/AndroidComposeView;->getSemanticsOwner()"):
            s = f.read_text()
            # такой же геттер есть у захвата контента, нужен делегат специальных возможностей
            if "AccessibilityManager$AccessibilityStateChangeListener" in s:
                found += getter.findall(re.sub(r"\n\s*\.line \d+", "", s))
        if len(found) != 1:
            raise PatchError(f"карта узлов semantics: найдено {len(found)}")
        names.update(SEMANTICS_DELEGATE=found[0][2], SEMANTICS_STALE=found[0][3], SEMANTICS_NODES=found[0][0])
    except PatchError as e:
        log(f"  карта semantics не найдена ({e}), Clawd будет сидеть по запомненному месту")
    try:
        # AbstractComposeView: единственный класс со строкой «Cannot add views to»
        base = class_of(sm.one_with('"Cannot add views to'))
        views = []
        for f in sm.files_with(f".super {base}"):
            m = re.search(r"^\.method public final setContent\((L[^;]+;)\)V$", f.read_text(), re.M)
            if m and ".method public constructor <init>(Landroid/content/Context;)V" in f.read_text():
                views.append((class_of(f), m.group(1)))
        if len(views) != 1:
            raise PatchError(f"ComposeView: найдено {len(views)}")
        f = sm.one_with('"clawd-loop-cel"')
        ms = re.findall(r"^\.method public static final (\w+)\((L[^;]+;)ZZIL[^;]+;II\)V$", f.read_text(), re.M)
        if len(ms) != 1:
            raise PatchError(f"Clawd(modifier, Z, Z, I, composer, changed, default): найдено {len(ms)}")
        # Modifier (компаньон): R8 убрал у Clawd значение модификатора по умолчанию, его надо передать.
        # Это объект, реализующий тип модификатора, с toString() == "Modifier".
        modifier = [class_of(m) for m in sm.files_with(f".implements {ms[0][1]}")
                    if re.search(r'const-string (\w+), "Modifier"\s+return-object \1', m.read_text())]
        if len(modifier) != 1:
            raise PatchError(f"Modifier: найдено {len(modifier)}")
        names.update(COMPOSE_VIEW=views[0][0], FUNCTION2=views[0][1], CLAWD=class_of(f), CLAWD_METHOD=ms[0][0],
                     MODIFIER=modifier[0])
    except PatchError as e:
        log(f"  Clawd приложения не найден ({e}), Clawd не будет")
    names.update(CLAWD_ANIM="", CLAWD_SHEET="", CLAWD_FRAMES="", CLAWD_FRAMES_METHOD="")
    try:
        # анимации Clawd для реакций: enum с кадрами (Jumping, Dancing, Laptop, Guitar, NodOff, Sleep, WakeUp,
        # Warning), его метод () -> лист кадров, и функция (лист, палитра, рамка) -> ArrayList<Bitmap-обёрток>
        anims = [f for f in sm.files_with('"NodOff"')
                 if all(x in f.read_text() for x in ('"Guitar"', '"WakeUp"', '"Warning"', '"Jumping"'))
                 and re.search(r"^\.class [^\n]*\benum\b", f.read_text(), re.M)]
        if len(anims) != 1:
            raise PatchError(f"анимации Clawd: найдено {len(anims)}")
        anim = class_of(anims[0])
        sheets = re.findall(r"^\.method public final (\w+)\(\)(L[^;]+;)$", anims[0].read_text(), re.M)
        sheets = [x for x in sheets if x[1] != anim]
        if len(sheets) != 1:
            raise PatchError(f"лист кадров Clawd: найдено {len(sheets)}")
        sheet = sheets[0][1]
        frames = []
        for f in sm.files_with("Landroid/graphics/Bitmap;->createBitmap([IIILandroid/graphics/Bitmap$Config;)"):
            frames += [(class_of(f), m) for m in re.findall(r"^\.method public static final (\w+)\(" + re.escape(sheet)
                                                            + r"L[^;]+;L[^;]+;\)Ljava/util/ArrayList;$", f.read_text(), re.M)]
        if len(frames) != 1:
            raise PatchError(f"кадры Clawd в Bitmap: найдено {len(frames)}")
        names.update(CLAWD_ANIM=anim, CLAWD_SHEET=sheets[0][0], CLAWD_FRAMES=frames[0][0], CLAWD_FRAMES_METHOD=frames[0][1])
    except PatchError as e:
        log(f"  анимации Clawd не найдены ({e}), Clawd не будет реагировать на ответы")
    return names


def _signature(header):
    """Статический ли метод и типы его параметров, по строке .method."""
    m = re.match(r"\.method (.*?)([\w<>$]+)\(([^)]*)\)", header)
    return " static " in f" {m.group(1)} ", PARAM.findall(m.group(3))


def _preg(static, types, i):
    """Регистр параметра i метода: p0 — this у нестатических, J и D занимают два регистра."""
    return f"p{(0 if static else 1) + sum(2 if t in ('J', 'D') else 1 for t in types[:i])}"


def _range_regs(first, last):
    return [f"v{i}" for i in range(int(first[1:]), int(last[1:]) + 1)]


ITEM_CALL = (r"invoke-static/range \{(\w+) \.\. (\w+)\}, (L[^;]+;)->(\w+)\((Ljava/lang/String;[^)]*)\)V\n")


def _defaults(text, reg):
    """Маска параметров по умолчанию — константа в последнем регистре диапазона вызова пункта."""
    vals = re.findall(r"const(?:/16|/4)? " + reg + r", (-?0x[0-9a-f]+)\n", text)
    return str(int(vals[-1], 16)) if vals else "0"


def _owners(method, header, skip_type, call):
    """В начало метода: Export.<call>(p) для лямбды (this, в нём захваченные поля) и каждого объектного
    параметра, кроме композера и обобщённых Object-параметров лямбд (первый вызов сбрасывает список)."""
    static, types = _signature(header)
    regs = [] if static else ["p0"]
    regs += [_preg(static, types, i) for i, t in enumerate(types)
             if t.startswith("L") and t not in (skip_type, "Ljava/lang/String;", "Ljava/lang/Object;")]
    lines, first = [], True
    for r in regs:
        lines.append(f"    invoke-static/range {{{r} .. {r}}}, {MOD}Export;->{call if first else call + 'Also'}"
                     "(Ljava/lang/Object;)V\n")
        first = False
    return re.sub(r"(\.locals \d+\n)", lambda x: x.group(1) + "\n" + "\n".join(lines), method, count=1)


def patch_chat_menu(sm, dec):
    """Пункт «Скачать .md» в меню «⋮» чата и сессии Code.

    Чат: пункт «На главный экран» (add_to_home) — своя Compose-функция item(label, onClick, modifier, painter, ...).
    Её зовёт статическая функция всего меню; после этого вызова, там, где ветки сходятся, зовём
    Export.menu(composer), а в начале меню запоминаем его параметры-лямбды: в них uuid беседы.
    Code: пункты рисует функция меню сессии по одному; после пункта «Поделиться» (session_menu_share)
    зовём Export.codeMenu(composer), параметры функции (onClick пункта) — Export.codeOwner.
    Возвращает имена для Names.java."""
    names = {"MENU_ITEM": "", "MENU_ITEM_METHOD": "", "MENU_DEFAULTS": "0", "PAINTER": "", "PAINTER_METHOD": "",
             "ICON": ""}
    public = (dec / "res/values/public.xml").read_text()

    # ---- чат ----
    m = re.search(r'name="add_to_home" id="(0x[0-9a-f]+)"', public)
    candidates, composer_type = [], None
    if m:
        rid = m.group(1)
        block = re.compile(r"const \w+, " + rid + r"\n[\s\S]{0,600}?invoke-static \{\w+, \w+\}, (L[^;]+;)->(\w+)"
                           r"\((L[^;]+;)(L[^;]+;)\)L[^;]+;[\s\S]{0,2500}?" + ITEM_CALL)
        for f in sm.files_with(f", {rid}"):
            for method in re.split(r"(?=^\.method )", f.read_text(), flags=re.M):
                mm = block.search(method)
                if not mm:
                    continue
                header = method.split("\n", 1)[0]
                static, types = _signature(header)
                name = re.match(r"\.method .*?([\w<>$]+)\(", header).group(1)
                candidates.append(f"{class_of(f)}->{name}({''.join(types)})V")
                composer_type = mm.group(4)
                names.update(PAINTER=mm.group(1), PAINTER_METHOD=mm.group(2), ICON=mm.group(3),
                             MENU_ITEM=mm.group(7), MENU_ITEM_METHOD=mm.group(8),
                             MENU_DEFAULTS=_defaults(mm.group(0), mm.group(6)))
    hooked = 0
    for item_fn in candidates:  # функция пункта: та, которую зовёт статическая функция меню
        if hooked:
            break
        call = re.compile(r"invoke-\w+(?:/range)? \{([^}]*)\}, " + re.escape(item_fn) + r"\n([\s\S]{0,600}?)"
                          r"goto(?:/16)? (:goto_\w+)\n")
        for f in sm.files_with(item_fn):
            text = f.read_text()
            out, changed = [], False
            for method in re.split(r"(?=^\.method )", text, flags=re.M):
                header = method.split("\n", 1)[0]
                mm = call.search(method)
                if not mm or " static " not in f" {header} ":
                    out.append(method)  # нестатические вызовы — перезапуск самой функции пункта
                    continue
                args = mm.group(1).split(", ") if " .. " not in mm.group(1) else _range_regs(*mm.group(1).split(" .. "))
                _, types = _signature(".method static x(" + item_fn.split("(", 1)[1].split(")")[0] + ")V")
                instance = 0 if "invoke-static" in mm.group(0) else 1
                composer = args[instance + types.index(composer_type)]
                hook = f"    invoke-static/range {{{composer} .. {composer}}}, {MOD}Export;->menu(Ljava/lang/Object;)V\n\n"
                method = re.sub(r"(\n\s*" + re.escape(mm.group(3)) + r"\n)", lambda x: x.group(1) + hook, method, count=1)
                method = _owners(method, header, composer_type, "owner")
                out.append(method)
                changed = True
                hooked += 1
            if changed:
                f.write_text("".join(out))
    log(f"  меню чата: пункт .md {'добавлен' if hooked else 'не добавлен (не нашёл место)'}")

    # ---- Code ----
    m = re.search(r'name="session_menu_share" id="(0x[0-9a-f]+)"', public)
    code = 0
    if m and composer_type:
        rid = m.group(1)
        block = re.compile(r"const \w+, " + rid + r"\n[\s\S]{0,1200}?" + ITEM_CALL + r"([\s\S]{0,300}?)(goto(?:/16)? :goto_\w+\n)")
        for f in sm.files_with(f", {rid}"):
            text = f.read_text()
            out, changed = [], False
            for method in re.split(r"(?=^\.method )", text, flags=re.M):
                mm = block.search(method)
                header = method.split("\n", 1)[0]
                types = PARAM.findall(mm.group(5)) if mm else []
                if not mm or composer_type not in types:
                    out.append(method)
                    continue
                composer = _range_regs(mm.group(1), mm.group(2))[types.index(composer_type)]
                hook = f"invoke-static/range {{{composer} .. {composer}}}, {MOD}Export;->codeMenu(Ljava/lang/Object;)V\n\n    "
                pos = mm.start(7)
                method = method[:pos] + hook + method[pos:]
                method = _owners(method, header, composer_type, "codeOwner")
                out.append(method)
                changed = True
                code += 1
            if changed:
                f.write_text("".join(out))
    log(f"  меню Code: пункт .md {'добавлен' if code else 'не добавлен (не нашёл место)'}")

    # ---- классы событий сессии Code для выгрузки ----
    names.update(session_names(sm))
    names.update(title_names(sm))
    return names


def title_names(sm):
    """Класс настройки TITLE беседы: реализует тот же интерфейс, что Model(model) — для имени файла .md."""
    try:
        model = sm.one_with('"Model(model="').read_text()
        iface = re.search(r"^\.implements (L[^;]+;)$", model, re.M).group(1)
        for f in sm.files_with('"Title(title="'):
            t = f.read_text()
            if re.search(r"^\.implements " + re.escape(iface) + "$", t, re.M):
                field = re.search(r"^\.field public final (\w+):Ljava/lang/String;$", t, re.M).group(1)
                return {"CONV_TITLE": {"cls": class_of(f)[1:-1].replace("/", "."), "title": field}}
    except (PatchError, AttributeError):
        pass
    log("  класс названия беседы не найден, .md назовётся по первому сообщению")
    return {"CONV_TITLE": {}}


def session_names(sm):
    """Классы и поля событий сессии Code (kotlinx.serialization), из которых собирается переписка."""
    base = "com.anthropic.claude.sessions.types."
    out = {"SESSION_EVENT": {}, "SESSION_ASSISTANT": {}, "SESSION_USER": {}, "SESSION_TEXT": {}, "SESSION_USER_TEXT": {}}
    try:
        for key, serial, fields in (("SESSION_EVENT", "SdkMessageEvent", ("message", "uuid", "session_id")),
                                    ("SESSION_ASSISTANT", "SdkAssistantMessage", ("content",)),
                                    ("SESSION_USER", "SdkNonAssistantMessage", ("role", "content")),
                                    ("SESSION_TEXT", "ContentBlock.Text", ("text",)),
                                    ("SESSION_USER_TEXT", "ApiUserMessageContent.Text", ("text",))):
            ser = Serial(sm, base + serial)
            out[key] = dict(cls=ser.cls[1:-1].replace("/", "."), **ser.fields(*fields))
            if key == "SESSION_EVENT":
                # каждое разобранное событие — в память мода (история сессии и поток): Export.event(this)
                text = ser.file.read_text()
                method = re.search(r"\.method public synthetic constructor <init>\(I[\s\S]*?\.end method", text).group(0)
                hooked = method.replace("    return-void\n", f"    invoke-static/range {{p0 .. p0}}, {MOD}Export;->"
                                        "event(Ljava/lang/Object;)V\n\n    return-void\n")
                ser.file.write_text(text.replace(method, hooked, 1))
    except PatchError as e:
        log(f"  события Code не разобраны ({e}), выгрузки Code не будет")
        return {k: {} for k in out}
    return out


def patch_google_login(sm, dec, function0):
    """Кнопка «Продолжить с Google»: вместо входа (он не работает в чужом пакете) окно с подсказкой."""
    m = re.search(r'name="login_welcome_google_login_with_google_button" id="(0x[0-9a-f]+)"',
                  (dec / "res/values/public.xml").read_text())
    if not m:
        raise PatchError("нет строки login_welcome_google_login_with_google_button")
    # без \n в строке поиска: grep -F считает перевод строки разделителем шаблонов
    content = {class_of(f) for f in sm.files_with(f", {m.group(1)}")
               if re.search(r", " + m.group(1) + r"$", f.read_text(), re.M)}
    if not content:
        raise PatchError("не нашёл кнопку Google")
    # компонент кнопки создаёт лямбду с её содержимым и получает onClick: Function0
    pat = re.compile(r"^\.method public static final \w+\(([^)]*)\)V\n\s+\.locals (\d+)\n", re.M)
    hits = 0
    for c in content:
        for f in sm.files_with(f"{c}-><init>"):
            s = f.read_text()
            out, pos = [], 0
            for mm in pat.finditer(s):
                end = s.find("\n.end method", mm.end())
                body = s[mm.end():end]
                if f"new-instance" not in body or not re.search(r"new-instance \w+, " + re.escape(c) + "\n", body):
                    continue
                params = re.findall(r"\[*(?:L[^;]+;|[ZBSCIFJD])", mm.group(1))
                if params.count(function0) != 1:
                    continue
                reg = 0
                for p in params:
                    if p == function0:
                        break
                    reg += 2 if p in ("J", "D") else 1
                hook = (f"\n    invoke-static/range {{p{reg} .. p{reg}}}, {MOD}Bridge;->"
                        "googleLogin(Ljava/lang/Object;)Ljava/lang/Object;\n\n"
                        f"    move-result-object p{reg}\n\n    check-cast p{reg}, {function0}\n")
                out.append(s[pos:mm.end()] + hook)
                pos = mm.end()
                hits += 1
            if out:
                f.write_text("".join(out) + s[pos:])
    if hits != 1:
        raise PatchError(f"кнопка Google: найдено мест {hits}, ожидалось 1")


def move_patched(sm):
    """Пропатченные классы — в отдельный smali_classesN. Основной classes.dex почти упирается в лимит
    65536 ссылок на методы, и каждая ссылка на код мода может его переполнить."""
    new = sm.dec / f"smali_classes{len(sm.dirs) + 1}"
    moved = 0
    for f in sm.files_with("Lcat/narezany/mods/"):
        src_dir = next(d for d in sm.dirs if d in f.parents)
        dest = new / f.relative_to(src_dir)
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(f), dest)
        moved += 1
    sm.dirs.append(new)
    return moved


# ---------------------------------------------------------------- сборка

def limit_abi(dec, abi):
    if abi == "all":
        return
    lib = dec / "lib"
    if not (lib / abi).exists():
        raise PatchError(f"в APK нет библиотек для {abi}")
    for d in lib.iterdir():
        if d.name != abi:
            shutil.rmtree(d)
    # сжатые нативные либы, чтобы APK был меньше
    m = dec / "AndroidManifest.xml"
    m.write_text(m.read_text().replace('android:extractNativeLibs="false"', 'android:extractNativeLibs="true"'))
    y = dec / "apktool.yml"
    y.write_text(re.sub(r"\n\s*- so\n", "\n", y.read_text()))


def build_dex(work, tools, names):
    out = work / "modbuild"
    shutil.rmtree(out, ignore_errors=True)
    gen = out / "gen/cat/narezany/mods"
    gen.mkdir(parents=True)
    body = ""
    for k, v in sorted(names.items()):
        if isinstance(v, list):
            body += f"    static final int[] {k} = {{{', '.join('0x%08X' % c for c in v)}}};\n"
        elif isinstance(v, dict):  # элемент -> поле
            body += f'    static final String {k} = "{",".join(f"{a}={b}" for a, b in v.items())}";\n'
        elif v.startswith("L") and v.endswith(";"):  # класс
            body += f'    static final String {k} = "{v[1:-1].replace("/", ".")}";\n'
        else:
            body += f'    static final String {k} = "{v}";\n'

    (gen / "Names.java").write_text("package cat.narezany.mods;\n\n/** Сгенерировано патчером. */\n"
                                    f"final class Names {{\n{body}}}\n")
    sources = [str(p) for p in (HERE / "src").rglob("*.java")] + [str(gen / "Names.java")]
    classes = out / "classes"
    classes.mkdir()
    run(["javac", "--release", "8", "-nowarn", "-encoding", "UTF-8", "-cp", tools / "android.jar", "-d", classes, *sources])
    dex = out / "dex"
    dex.mkdir()
    run(["java", "-cp", tools / "r8.jar", "com.android.tools.r8.D8", "--release", "--min-api", "29",
         "--lib", tools / "android.jar", "--output", dex, *map(str, classes.rglob("*.class"))])
    return dex / "classes.dex"


def add_dex(apk, dex):
    with zipfile.ZipFile(apk) as z:
        nums = [int(m.group(1) or 1) for n in z.namelist() for m in [re.fullmatch(r"classes(\d*)\.dex", n)] if m]
    name = f"classes{max(nums) + 1}.dex"
    with zipfile.ZipFile(apk, "a", zipfile.ZIP_DEFLATED) as z:
        z.write(dex, name)
    return name


def ensure_keystore():
    if KEYSTORE.exists():
        return
    KEYSTORE.parent.mkdir(parents=True, exist_ok=True)
    log(f"ВНИМАНИЕ: ключа подписи нет, создаю новый {KEYSTORE}. APK с ним не встанет поверх MargyC, "
        "подписанного другим ключом.")
    run(["keytool", "-genkeypair", "-keystore", KEYSTORE, "-alias", KEY_ALIAS, "-storepass", KEY_PASS,
         "-keypass", KEY_PASS, "-keyalg", "RSA", "-keysize", "2048", "-validity", "36500",
         "-dname", "CN=narezany, O=Claude Mods"])


def sign(apk, out, tools):
    ensure_keystore()
    tmp = pathlib.Path(tempfile.mkdtemp())
    run(["java", "-jar", tools / "uber-apk-signer.jar", "-a", apk, "-o", tmp, "--ks", KEYSTORE,
         "--ksAlias", KEY_ALIAS, "--ksPass", KEY_PASS, "--ksKeyPass", KEY_PASS, "--allowResign"])
    signed = next(tmp.glob("*.apk"))
    shutil.move(str(signed), out)
    shutil.rmtree(tmp)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("input", help="XAPK/APKS/APKM, папка со сплитами или APK оригинального Claude")
    ap.add_argument("-o", "--output", default="ClaudeMods.apk")
    ap.add_argument("--abi", default="arm64-v8a", help="оставить только эту архитектуру (all — все)")
    ap.add_argument("--work", help="рабочая папка (по умолчанию временная)")
    ap.add_argument("--keep", action="store_true", help="не удалять рабочую папку")
    a = ap.parse_args()

    tools = ensure_tools()
    work = pathlib.Path(a.work or tempfile.mkdtemp(prefix="claude-mods-"))
    work.mkdir(parents=True, exist_ok=True)
    try:
        merged = merge_input(a.input, work, tools)
        dec = work / "dec"
        log("apktool d")
        run(["java", "-jar", tools / "apktool.jar", "d", "-f", merged, "-o", dec])

        log("патчи")
        app = patch_manifest(dec)
        patch_resources(dec)
        patch_icons(dec)
        sm = Smali(dec)
        patch_install_source(sm)
        patch_crash_log(sm, app)
        names = patch_drawer(sm)
        patch_translate(sm)
        patch_prompt(sm)
        patch_rest_body(sm)
        patch_account(sm)
        log("  шрифты: Typeface.Builder(assets) %d, вызовов %d" % patch_fonts(sm))
        names.update(patch_models(sm))
        names.update(find_clawd(sm))
        names.update(patch_chat_menu(sm, dec))
        patch_google_login(sm, dec, names["FUNCTION0"])
        palettes = patch_theme(sm)
        log(f"  перенесено пропатченных классов: {move_patched(sm)}")
        limit_abi(dec, a.abi)
        for k, v in names.items():
            log(f"  {k} = {v}")
        log(f"  палитры: тёмная {len(palettes['DARK_PALETTE'])}, светлая {len(palettes['LIGHT_PALETTE'])} цветов")
        names.update(palettes)

        log("apktool b")
        unsigned = work / "unsigned.apk"
        run(["java", "-jar", tools / "apktool.jar", "b", dec, "-o", unsigned])
        log("код мода")
        log(f"  {add_dex(unsigned, build_dex(work, tools, names))}")
        log("подпись")
        sign(unsigned, a.output, tools)
        log(f"готово: {a.output}")
    except PatchError as e:
        sys.exit(f"ошибка: {e}")
    finally:
        if not a.keep and not a.work:
            shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    main()
