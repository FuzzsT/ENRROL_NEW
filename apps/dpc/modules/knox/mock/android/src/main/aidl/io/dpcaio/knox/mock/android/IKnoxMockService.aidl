package io.dpcaio.knox.mock.android;
interface IKnoxMockService {
    String getLicenseState();
    String setPackageHidden(String packageName, boolean hidden);
    String setPackageSuspended(String packageName, boolean suspended);
}
