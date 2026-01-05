package ohi.andre.consolelauncher.managers.xml.classes;

public class XMLPrefsEntry {

    public String key, value;

    public XMLPrefsEntry(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj) return true;
        if(obj instanceof XMLPrefsEntry) {
            XMLPrefsEntry other = (XMLPrefsEntry) obj;
            return this.key.equals(other.key); 
        } else if(obj instanceof XMLPrefsSave) {
            return this.key.equals(((XMLPrefsSave) obj).label());
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return key + " --> " + value;
    }
}
