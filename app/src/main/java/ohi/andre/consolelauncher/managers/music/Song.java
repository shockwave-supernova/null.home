package ohi.andre.consolelauncher.managers.music;

import java.io.File;

import it.andreuzzi.comparestring2.StringableObject;

public class Song implements StringableObject {

    private final long id;
    private final String title;
    private final String path;
    private final String lowercaseTitle;

    // Конструктор для песен из MediaStore (по ID)
    public Song(long songID, String songTitle) {
        this.id = songID;
        this.title = (songTitle != null) ? songTitle : "Unknown Track";
        this.path = null; // Для MediaStore путь не нужен, используем URI
        this.lowercaseTitle = this.title.toLowerCase();
    }

    // Конструктор для песен из файлов (File Explorer)
    public Song(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf(".");
        
        // Безопасное удаление расширения файла
        if (dot > 0) {
            name = name.substring(0, dot);
        }

        this.title = name;
        this.path = file.getAbsolutePath();
        this.id = -1; // -1 используется как флаг того, что это файл, а не ID из базы данных
        this.lowercaseTitle = this.title.toLowerCase();
    }

    public long getID() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getPath() {
        return path;
    }

    // Реализация интерфейса StringableObject для сортировки и поиска в T-UI
    @Override
    public String getLowercaseString() {
        return lowercaseTitle;
    }

    @Override
    public String getString() {
        return title;
    }

    @Override
    public String toString() {
        return title;
    }
}
