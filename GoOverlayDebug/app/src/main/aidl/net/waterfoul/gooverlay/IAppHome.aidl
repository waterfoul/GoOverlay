// IAppHome.aidl
package net.waterfoul.gooverlay;

interface IAppHome {
    String getSettingsIntent();
    void enable();
    void disable();
}