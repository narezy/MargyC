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
            return Mods.app().getResources().getConfiguration().getLocales().get(0).getLanguage().equals("ru");
        } catch (Exception e) {
            return java.util.Locale.getDefault().getLanguage().equals("ru");
        }
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
    }
}
