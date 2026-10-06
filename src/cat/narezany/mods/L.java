package cat.narezany.mods;

import java.util.HashMap;
import java.util.Map;

/**
 * Язык экранов мода: строки пишутся по-русски, L.t() отдаёт английский перевод, если язык приложения
 * не русский. Строк без перевода в словаре нет смысла бояться: вернётся русская.
 */
final class L {
    private static final Map<String, String> EN = new HashMap<String, String>();

    private L() {}

    static boolean ru() {
        try {
            return slavic(Mods.app().getResources().getConfiguration().getLocales().get(0).getLanguage());
        } catch (Exception e) {
            return slavic(java.util.Locale.getDefault().getLanguage());
        }
    }

    /** Для белорусского и казахского экраны мода по-русски: так понятнее, чем по-английски. */
    private static boolean slavic(String lang) {
        return lang.equals("ru") || lang.equals("be") || lang.equals("kk");
    }

    static String t(String ru) {
        if (ru == null || ru()) {
            return ru;
        }
        String en = EN.get(ru);
        return en != null ? en : ru;
    }

    private static void put(String ru, String en) {
        EN.put(ru, en);
    }

    static {
        // экран модов
        put("Моды", "Mods");
        put("Язык", "Language");
        put("Оформление", "Appearance");
        put("Питомец", "Pet");
        put("Мемные модели", "Meme models");
        put("Свои моды", "Custom mods");
        put(" от narezany", " by narezany");
        put("Русский язык", "Russian language");
        put("Весь интерфейс на русском. Выключишь — будет английский.", "The whole app in Russian. Turn off for English.");
        put("Весь интерфейс на русском. Выключишь — вернётся язык системы.", "The whole app in Russian. Turn off for the system language.");
        put("Нужен Android 13 или новее. На старых версиях поставь русский языком системы, перевод подхватится сам.",
                "Needs Android 13 or newer. On older versions set Russian as the system language and the translation is picked up.");
        put("Не получилось сменить язык", "Couldn't change the language");
        put("Акцентный цвет", "Accent color");
        put("Своя тема", "Custom theme");
        put("Свои цвета для тёмной и светлой темы Claude.", "Your own colors for Claude's dark and light themes.");
        put("Тёмная тема", "Dark theme");
        put("Светлая тема", "Light theme");
        put("Скопировать мою тему", "Copy my theme");
        put("Текстом, чтобы отправить другу.", "As text, to send to a friend.");
        put("Тема скопирована", "Theme copied");
        put("Готово", "Done");
        put("Вставить тему", "Paste a theme");
        put("Из буфера обмена. Тему может написать и Claude, просто попроси.", "From the clipboard. Claude can write one too, just ask.");
        put("Это не тема", "Not a theme");
        put("В буфере обмена нет темы MargyC. Скопируй её целиком, вместе со строками вида «dark 151515: #0E1116».",
                "There is no MargyC theme in the clipboard. Copy all of it, with lines like \"dark 151515: #0E1116\".");
        put("Понятно", "OK");
        put("Тема применена", "Theme applied");
        put("Прочитано цветов: ", "Colors read: ");
        put(". Они появятся после перезапуска приложения.", ". They show up after the app restarts.");
        put("Канал MargyC", "MargyC channel");
        put("Новые обновления", "New updates");
        put("Подписаться", "Join");
        put("MargyC на GitHub", "MargyC on GitHub");
        put("Исходники, свои моды и свежий APK", "Source, custom mods and the latest APK");
        put("Открыть", "Open");
        put("Написать автору", "Message the author");
        put("@narezany в Telegram", "@narezany on Telegram");
        put("Дополнить системный промпт", "Extend the system prompt");
        put("Claude получает текст пресета вместе с каждым сообщением. В чате его не видно.",
                "Claude gets the preset with every message. It is not shown in the chat.");
        put("Пресет", "Preset");
        put("Текст пресета", "Preset text");
        put("О моде, устройстве и как писать темы. Не меняется.", "About the mod, your device and how to write themes. Read only.");
        put("О моде и устройстве. Не меняется.", "About the mod and your device. Read only.");
        put("Всё о MargyC: функции, включённые моды, темы и палитра Material You. Не меняется.",
                "Everything about MargyC: features, enabled mods, themes and the Material You palette. Read only.");
        put("Развёрнутый", "Extended");
        put("Обычный", "Basic");
        put("Суперразвёрнутый", "Super extended");
        put("Новый пресет", "New preset");
        put("Пресет «", "Preset \"");
        put("Копия в новый", "Copy to new");
        put("Мой пресет", "My preset");
        put("Закрыть", "Close");
        put("Название", "Name");
        put("Что Claude должен знать. Лучше по-английски.", "What Claude should know. English works best.");
        put("Удалить", "Delete");
        put("Отмена", "Cancel");
        put("Сохранить", "Save");
        put("Кай одобряет MargyC", "Kai approves MargyC");
        put("Clawd на поле ввода", "Clawd on the message box");
        put("Сидит на поле ввода в чате и в Code и поднимается вместе с ним. Зажми и тащи, чтобы пересадить, коснись — анимация заново.",
                "Sits on the message box in chat and Code and rises with it. Hold and drag to move him, tap to replay his animation.");
        put("Отвечает ", "Answered by ");
        put("Новая модель", "New model");
        put("Своя модель сверху в списке моделей, например «Fable 6969». Отвечает настоящая, какую выберешь.",
                "Your own model on top of the model list, like \"Fable 6969\". A real model of your choice answers.");
        put("Вставить модель", "Paste a model");
        put("Из буфера обмена: модель, которой с тобой поделились.", "From the clipboard: a model someone shared with you.");
        put("Журнал", "Journal");
        put("Что делали хуки мемных моделей. Если что-то не работает, скопируй и отправь автору.",
                "What the mod's hooks did. If something doesn't work, copy it and send it to the author.");
        put("Журнал мемных моделей", "MargyC journal");
        put("Скопировать", "Copy");
        put("Скопировано", "Copied");
        put("Нет списка моделей", "No model list yet");
        put("Мод ещё не видел список моделей Claude. Открой чат, чтобы приложение его загрузило, и возвращайся.",
                "The mod hasn't seen Claude's model list yet. Open a chat so the app loads it, then come back.");
        put("Название, например Fable 6969", "Name, e.g. Fable 6969");
        put("Описание под названием", "Description under the name");
        put("Отвечает: ", "Answered by: ");
        put("Кто отвечает на самом деле", "Who really answers");
        put("Системный промпт этой модели, например: ты Fable 6969 и всегда упоминаешь 6969. ",
                "System prompt of this model, e.g.: you are Fable 6969 and always mention 6969. ");
        put("Добавляется к промпту мода.", "Added to the mod's prompt.");
        put("Мемная модель", "Meme model");
        put("Поделиться", "Share");
        put("Модель скопирована", "Model copied");
        put("Это не модель", "Not a model");
        put("В буфере обмена нет мемной модели. Скопируй её целиком, начиная со строки «MargyC model».",
                "There is no meme model in the clipboard. Copy all of it, starting with the \"MargyC model\" line.");
        put("Модель добавлена", "Model added");
        put("Добавлена одна модель.", "One model added.");
        put("Добавлено моделей: ", "Models added: ");
        put(" Она появится в списке моделей после перезапуска.", " It shows up in the model list after a restart.");
        put("Ошибка: ", "Error: ");
        put("Установить мод", "Install a mod");
        put("Файл .mcmod: скомпилированный мод с manifest.json.", "A .mcmod file: a compiled mod with manifest.json.");
        put("Нет приложения для выбора файла", "No app to pick a file");
        put("Как написать свой мод", "How to write a mod");
        put("Документация и пример на GitHub.", "Docs and an example on GitHub.");
        put("Мод установлен", "Mod installed");
        put(" от ", " by ");
        put(". Он заработает после перезапуска Claude.", ". It starts working after Claude restarts.");
        put("Это не мод", "Not a mod");
        put("Не получилось установить: ", "Couldn't install: ");
        put("Удалить мод?", "Delete the mod?");
        put(" пропадёт после перезапуска Claude.", " goes away after Claude restarts.");
        put("#%06X. Заменяет оранжевый и синий цвет выбора во всём приложении.",
                "#%06X. Replaces the orange and the blue selection color across the app.");
        put("Заменяет оранжевый и синий цвет выбора во всём приложении.", "Replaces the orange and the blue selection color across the app.");
        put("Как в Claude", "As in Claude");
        put("Изменено цветов: ", "Colors changed: ");
        put("Диалоги", "Conversations");
        put("Скачивание диалогов", "Conversation download");
        put("Пункт «Скачать диалог (.md)» в меню «⋮» чата. Файл сохраняется в Загрузки/MargyC.",
                "A \"Download conversation (.md)\" item in the chat's ⋮ menu. Files go to Downloads/MargyC.");
        put("Открыть диалог .md", "Open a .md conversation");
        put("Экспорт MargyC или любой .md с заголовками «## Ты» / «## Claude»: посмотреть как чат и продолжить в Claude.",
                "A MargyC export or any .md with \"## You\" / \"## Claude\" headings: view it as a chat and continue in Claude.");
        put("Продолжить в Claude", "Continue in Claude");
        put("Сообщений: ", "Messages: ");
        put("Не получилось открыть файл", "Couldn't open the file");
        put("Это предыдущий диалог, продолжим его. Вот он целиком:", "This is our previous conversation, let's continue it. Here it is:");
        // темы
        put("Фон и поверхности", "Background and surfaces");
        put("Текст и линии", "Text and lines");
        put("Акцент", "Accent");
        put("Синий", "Blue");
        put("Фиолетовый", "Purple");
        put("Красный", "Red");
        put("Зелёный", "Green");
        put("Жёлтый", "Yellow");
        put("Прозрачные", "Transparent");
        put(", было ", ", was ");
        put("было ", "was ");
        put("Сбросить все цвета", "Reset all colors");
        put("фирменный оранжевый (меняется и акцентом)", "Claude orange (also changed by the accent)");
        put("тёмный фирменный оранжевый", "dark Claude orange");
        put("выбранный пункт, ссылки (меняется и акцентом)", "selected item, links (also changed by the accent)");
        put("включённый переключатель (меняется и акцентом)", "switch on (also changed by the accent)");
        put("фон экранов и окон", "screen and dialog background");
        put("карточки, строки настроек, поле ввода", "cards, settings rows, message box");
        put("сообщения пользователя, приподнятые элементы", "your messages, raised elements");
        put("основной текст", "main text");
        put("второстепенный текст", "secondary text");
        put("заголовки разделов", "section titles");
        put("выключенный переключатель, линии", "switch off, lines");
        // общее
        put("Перезапустите Claude, чтобы применить изменения", "Restart Claude to apply the changes");
        put("Перезапустить", "Restart");
        put("Сбросить", "Reset");
        put("Назад", "Back");
        put("Пока пусто.", "Nothing yet.");
        // вход, вылеты
        put("Вход через Google недоступен", "Google sign-in is not available");
        put("В MargyC вход через Google не работает: Google пускает только оригинальное приложение.\n\n"
                + "Введи ниже ту же почту, что у твоего Google-аккаунта, и войди по коду из письма. Откроется тот же "
                + "аккаунт, все чаты и подписка на месте.",
                "Google sign-in doesn't work in MargyC: Google only lets the original app in.\n\n"
                + "Enter the email of your Google account below and sign in with the code from the email. "
                + "It's the same account, with all your chats and your subscription.");
        put("MargyC: вылет", "MargyC: crash");
        put("Приложение вылетело в прошлый раз. Скопируй текст ниже и скинь его. Копия лежит в Загрузки/ClaudeMods.",
                "The app crashed last time. Copy the text below and send it. A copy is in Downloads/ClaudeMods.");
        // свои моды
        put("в архиве нет manifest.json", "manifest.json is missing in the archive");
        put("id в manifest.json: маленькие латинские буквы, цифры, . _ -", "manifest.json id: lowercase latin letters, digits, . _ -");
        put("в manifest.json нет entry (класс мода)", "manifest.json has no entry (the mod class)");
        put("в архиве нет classes.dex", "classes.dex is missing in the archive");
        put("мод для более новой версии MargyC (API ", "the mod needs a newer MargyC (API ");
        put("не удалось сохранить мод", "couldn't save the mod");
        // скачивание диалога
        put("Скачать диалог (.md)", "Download conversation (.md)");
        put("Не нашёл этот диалог в кэше приложения. Пролистай его до начала и попробуй ещё раз.",
                "This conversation isn't in the app's cache. Scroll it to the top and try again.");
        put("Сохранено: Загрузки/MargyC/", "Saved: Downloads/MargyC/");
        put("Не получилось сохранить: ", "Couldn't save: ");
        put("Размышления", "Thinking");
        put("Не нашёл переписку этой сессии. Открой её и попробуй ещё раз.", "Couldn't find this session's messages. Open it and try again.");
        put("Ты", "You");

        // MargyC 1.2
        put("это не шрифт TTF или OTF", "this is not a TTF or OTF font");
        put("Чистый чёрный фон для OLED-экранов. Светлая тема без изменений.", "Pure black background for OLED screens. The light theme stays as is.");
        put("Mocha в тёмной теме и Latte в светлой, акцент mauve.", "Mocha in the dark theme and Latte in the light one, mauve accent.");
        put("Холодные северные цвета, акцент frost.", "Cool northern colors, frost accent.");
        put("Тёмная фиолетовая классика. Светлая тема без изменений.", "The dark purple classic. The light theme stays as is.");
        put("Тёплые ретро-цвета, акцент оранжевый.", "Warm retro colors, orange accent.");
        put("Ночной Токио: тёмно-синий и неон. Светлая — Tokyo Day.", "Tokyo at night: deep blue and neon. Light is Tokyo Day.");
        put("Сепия", "Sepia");
        put("Тёплая бумага и чернила, мягко для глаз.", "Warm paper and ink, easy on the eyes.");
        put("Цвета из обоев телефона, как в системе.", "Colors from your wallpaper, like the system.");
        put("Нужен Android 11 или новее.", "Needs Android 11 or newer.");
        put("На телефоне не настроены ни отпечаток, ни лицо, ни PIN-код экрана блокировки.", "No fingerprint, face or screen lock PIN is set up on this phone.");
        put("Claude заблокирован", "Claude is locked");
        put("Разблокировать", "Unlock");
        put("Вход в Claude", "Unlock Claude");
        put("Сразу", "Immediately");
        put(" с", " s");
        put(" мин", " min");
        put("Защита", "Privacy");
        put("Галерея тем", "Theme gallery");
        put("Готовые темы: AMOLED, Material You, Catppuccin, Nord и другие.", "Ready themes: AMOLED, Material You, Catppuccin, Nord and more.");
        put("Шрифт", "Font");
        put("Реагирует на ответы", "Reacts to replies");
        put("Пока Claude отвечает, Clawd печатает на ноутбуке, а потом прыгает или танцует. Анимации — самого Claude.", "While Claude replies, Clawd types on a laptop, then jumps or dances. The animations are Claude's own.");
        put("Каталог модов", "Mod catalog");
        put("Моды из репозитория MargyC: установка в одно касание.", "Mods from the MargyC repository, installed in one tap.");
        put("Не получилось", "Something went wrong");
        put("Шрифт не подошёл: ", "This font did not work: ");
        put("Обычные цвета: своя тема и акцент выключаются.", "Default colors: custom theme and accent are turned off.");
        put("Системный", "System");
        put("С засечками (системный)", "Serif (system)");
        put("С засечками Claude", "Claude serif");
        put("Моноширинный", "Monospace");
        put("Свой файл", "Your own file");
        put("Anthropic Sans в интерфейсе, Anthropic Serif в ответах.", "Anthropic Sans in the interface, Anthropic Serif in replies.");
        put("Шрифт телефона (обычно Roboto или шрифт прошивки).", "The phone's font (usually Roboto or your ROM's font).");
        put("Anthropic Serif, шрифт ответов Claude, во всём приложении.", "Anthropic Serif, the font of Claude's replies, everywhere.");
        put("Системный шрифт с засечками.", "The system serif font.");
        put("Как в терминале. Код остаётся своим шрифтом в любом случае.", "Like a terminal. Code keeps its own font either way.");
        put("Файл .ttf или .otf с телефона.", "A .ttf or .otf file from your phone.");
        put("Блокировка", "App lock");
        put("Claude открывается по отпечатку, лицу или PIN-коду телефона.", "Claude opens with your fingerprint, face or phone PIN.");
        put("Блокировку не включить", "Can't turn on the lock");
        put("Спрашивать снова", "Ask again");
        put("Спрашивать снова через", "Ask again after");
        put("Каждый раз, когда Claude уходит в фон.", "Every time Claude goes to the background.");
        put("Скрывать в недавних", "Hide in recents");
        put("В списке открытых приложений вместо снимка чата пусто.", "Recent apps show a blank card instead of your chat.");
        put("Обновления", "Updates");
        put("Проверяю…", "Checking…");
        put("Не получилось проверить: ", "Couldn't check: ");
        put("У тебя последняя версия", "You have the latest version");
        put("Бета-версии", "Beta versions");
        put("Новые функции раньше всех, но в бете бывают ошибки. Ставится поверх, настройки сохраняются.", "New features first, but betas can have bugs. Installs over, settings are kept.");
        put("Вышла ", "Out now: ");
        put(". Нажми, чтобы скачать.", ". Tap to download.");
        put("У тебя ", "You have ");
        put(". Нажми, чтобы проверить.", ". Tap to check.");
        put("Загружаю…", "Loading…");
        put("Не получилось загрузить каталог: ", "Couldn't load the catalog: ");
        put("В каталоге пока пусто.", "The catalog is empty for now.");
        put("Установить", "Install");
        put("Установлен", "Installed");
        put("Обновить до v", "Update to v");
        put("Не установился", "Not installed");
        put("Сейчас у тебя MargyC ", "You have MargyC ");
        put(" Это бета: в ней новые функции, но могут быть ошибки.", " It is a beta: new features, but there can be bugs.");
        put("\n\nAPK скачается в браузере, установи его поверх, настройки сохранятся.", "\n\nThe APK downloads in the browser. Install it over this one, your settings are kept.");
        put("Вышла MargyC ", "MargyC ");
        put("Пропустить", "Skip");
        put("Позже", "Later");
        put("Скачать", "Download");
        put("Поддержать автора", "Support the author");
        put("Свободное ПО под лицензией GPL-3.0", "Free software under GPL-3.0");
        put("Перевод на карту", "Bank card transfer");
        put("Номер карты скопирован", "Card number copied");
        put("ЮMoney", "YooMoney");
        put("Донат через ЮMoney, картой любого банка.", "Donate via YooMoney with any bank card.");
        put("Лицензия", "License");
        put("GPL-3.0: код можно менять и распространять, но с исходниками и с указанием автора (narezany) и этих реквизитов для донатов.", "GPL-3.0: you may change and share the code, with the source, keeping the author (narezany) and these donation details.");
        put("Журнал MargyC", "MargyC journal");
        put("Что делали хуки и моды, включая прошлые запуски. Если что-то не работает, скопируй и отправь автору.", "What the hooks and mods did, including previous runs. If something does not work, copy it and send it to the author.");
        put("Очистить", "Clear");
        put("Журнал очищен", "Journal cleared");
        // 1.3
        put(" дн.", " days");
        put(" дн. подряд", " days in a row");
        put(" дн. подряд. Напиши, чтобы не прервать", " days in a row. Write to keep it going");
        put(" сообщений за день (", " messages in a day (");
        put(". Считается только на телефоне и никуда не отправляется.", ". Counted on the phone only and never sent anywhere.");
        put("Claude в цифрах", "Claude in numbers");
        put("Clawd с приветствием и сообщениями за сегодня. Ночью спит, днём за ноутбуком; коснись — подпрыгнет.", "Clawd with a greeting and today's messages. Sleeps at night, works on a laptop by day; tap him to make him jump.");
        put("«Доброе утро», «Добрый вечер», «Не спится?» и другие, с твоим именем.", "“Доброе утро”, “Добрый вечер”, “Не спится?” and more, with your name.");
        put("Баг! Коснись, чтобы ещё раз", "Bug! Tap to try again");
        put("Виджет на рабочий стол", "Home screen widget");
        put("Виноград", "Grape");
        put("Выключены", "Off");
        put("Глина", "Clay");
        put("Градиент", "Gradient");
        put("Графит", "Graphite");
        put("Дней с Claude: ", "Days with Claude: ");
        put("Доброе утро", "Good morning");
        put("Доброй ночи", "Good night");
        put("Добрый вечер", "Good evening");
        put("Добрый день", "Good afternoon");
        put("Зажми пустое место на рабочем столе, выбери «Виджеты» и найди Clawd в списке MargyC.", "Long-press an empty spot on the home screen, choose “Widgets” and find Clawd under MargyC.");
        put("Затемнение", "Dim");
        put("Затемнение: ", "Dim: ");
        put("Звезда Claude", "Claude spark");
        put("Звёзды", "Stars");
        put("Зимой снег, весной лепестки, осенью листья, летом ничего.", "Snow in winter, petals in spring, leaves in autumn, nothing in summer.");
        put("Игра с Clawd", "Clawd game");
        put("Иконка приложения", "App icon");
        put("Иконка сменена", "Icon changed");
        put("Картинка не подошла: ", "This image did not work: ");
        put("Коснись, чтобы написать Claude", "Tap to write to Claude");
        put("Коснись, чтобы прыгнуть", "Tap to jump");
        put("Лаунчеру может понадобиться несколько секунд. Если иконка пропала с рабочего стола, добавь её заново из списка приложений.", "The launcher may need a few seconds. If the icon disappeared from the home screen, add it again from the app list.");
        put("Лепестки сакуры", "Sakura petals");
        put("Лес", "Forest");
        put("Листопад", "Falling leaves");
        put("Любимая модель: ", "Favourite model: ");
        put("Мой Claude в цифрах (MargyC):", "My Claude in numbers (MargyC):");
        put("Мята", "Mint");
        put("Над полем ввода видно, сколько символов в сообщении.", "Shows above the message field how many characters the message has.");
        put("Найти в чате", "Find in chat");
        put("Насколько приглушить обои, чтобы текст читался.", "How much to dim the wallpaper so the text stays readable.");
        put("Ночь", "Night");
        put("Нужен Android 13 или новее. На старых версиях поставь нужный язык языком системы, перевод подхватится сам.", "Needs Android 13 or newer. On older versions set the language as the system language, the translation is picked up automatically.");
        put("Обои в чате", "Chat wallpaper");
        put("Обычный фон Claude.", "Claude's usual background.");
        put("Персик", "Peach");
        put("Пиксельный Clawd", "Pixel Clawd");
        put("По времени года", "By season");
        put("По-русски, по времени суток", "In Russian, by time of day");
        put("Поиск в чате", "Search in chat");
        put("При запуске Claude на секунду появляется Clawd на фоне твоей темы.", "When Claude starts, Clawd shows for a second on your theme's background.");
        put("Приветствие", "Greeting");
        put("Приходит с сервера Claude, обычно по-английски.", "Comes from Claude's server, usually in English.");
        put("Прыгай через баги. Коснись экрана — Clawd подпрыгнет.", "Jump over bugs. Tap the screen and Clawd jumps.");
        put("Пункт «Найти в чате» в том же меню «⋮»: вся беседа с подсветкой совпадений, стрелками — к следующему.", "A “Find in chat” item in the same “⋮” menu: the whole conversation with matches highlighted, arrows go to the next one.");
        put("Режим стримера", "Streamer mode");
        put("Рекорд ", "Best ");
        put("Рекорд: ", "Record: ");
        put("С ", "Since ");
        put("Сбросить статистику?", "Reset the stats?");
        put("Свои фразы", "Own phrases");
        put("Свой список. Строка — фраза; «утро:», «день:», «вечер:», «ночь:» в начале — для времени суток; {name} — имя.", "Your own list. One phrase per line; “morning:”, “day:”, “evening:”, “night:” at the start set the time of day; {name} is your name.");
        put("Свой экран запуска", "Custom splash");
        put("Своя картинка", "Own image");
        put("Своя картинка…", "Own image…");
        put("Сегодня сообщений: ", "Messages today: ");
        put("Сезонные эффекты", "Seasonal effects");
        put("Серия: ", "Streak: ");
        put("Снег", "Snow");
        put("Статистика", "Stats");
        put("Сумерки", "Dusk");
        put("Считается с установки 1.3, только на телефоне. Пока сообщений нет.", "Counted since installing 1.3, on the phone only. No messages yet.");
        put("Счётчик символов", "Character counter");
        put("Твои имя и почта скрыты везде в Claude, включая приветствие на главном экране. После перезапуска.", "Your name and email are hidden everywhere in Claude, including the home screen greeting. After a restart.");
        put("Фото из галереи. Под чатом, карточки сообщений остаются как есть.", "A photo from the gallery. Behind the chat; message cards stay as they are.");
        put("Чаще всего пишешь в ", "You write most often at ");
        put("Чаще всего пишу в ", "I write most often at ");
        put("Язык приложения", "App language");
        put("дней подряд сейчас", "days in a row now");
        put("дней с Claude", "days with Claude");
        put("любимая модель", "favourite model");
        put("серия ", "streak ");
        put("сообщений за самый активный день, ", "messages on the busiest day, ");
        put("сообщений отправлено", "messages sent");
        put("утро: Доброе утро, {name}\nвечер: Как прошёл день?\nПривет!", "morning: Good morning, {name}\nevening: How was your day?\nHi!");
        put("это не картинка", "this is not an image");
    }
}
