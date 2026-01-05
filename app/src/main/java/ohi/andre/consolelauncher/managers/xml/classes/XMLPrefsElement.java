package ohi.andre.consolelauncher.managers.xml.classes;

public interface XMLPrefsElement {
    XMLPrefsList getValues();
    void write(XMLPrefsSave save, String value);
    String[] delete();
    String path();
}
