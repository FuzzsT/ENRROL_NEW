package io.dpcaio.shizuku;

interface IAioShizukuUserService {
    String identity();
    int startActivity(String packageName, String className, int userId);
    int sendBroadcast(String action, String packageName, int userId);
    String listPermissions();
    int grantRuntimePermission(String packageName, String permissionName, int userId);
    int setAppOp(String packageName, String op, String mode, int userId);
    String getAppOps(String packageName, int userId);
    int setPackageEnabled(String packageName, boolean enabled, int userId);
    int installExistingPackage(String packageName, int userId);
    String readSetting(String namespaceName, String key, int userId);
    int writeSetting(String namespaceName, String key, String value, int userId);
    int deleteSetting(String namespaceName, String key, int userId);
    String listPermissionConfigFiles();
    String readPermissionConfig(String partitionName, String fileName);
    String listSysconfigFiles();
    String readSysconfigFile(String partitionName, String fileName);
}
