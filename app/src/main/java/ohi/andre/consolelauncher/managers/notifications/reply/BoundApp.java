package ohi.andre.consolelauncher.managers.notifications.reply;

import it.andreuzzi.comparestring2.StringableObject;

public class BoundApp implements StringableObject {

    public final int applicationId;
    public final String label;
    public final String packageName;

    private final String lowercaseLabel;

    public BoundApp(int applicationId, String packageName, String label) {
        this.applicationId = applicationId;
        this.packageName = packageName;
        this.label = label;
        this.lowercaseLabel = label.toLowerCase();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BoundApp boundApp = (BoundApp) obj;
        return applicationId == boundApp.applicationId;
    }

    @Override
    public int hashCode() {
        return applicationId;
    }

    @Override
    public String getLowercaseString() {
        return lowercaseLabel;
    }

    @Override
    public String getString() {
        return label;
    }
}
