// 构建期工具：把 jpackage 生成的按用户 MSI 改成可按用户或按机器安装的双用途包
// 用法：msipatch <源 MSI> <输出 MSI>
#include <windows.h>
#include <msi.h>
#include <msiquery.h>

#include <cstdio>
#include <string>

namespace {

bool Execute(MSIHANDLE db, const wchar_t* sql) {
    PMSIHANDLE view;
    UINT r = MsiDatabaseOpenViewW(db, sql, &view);
    if (r == ERROR_SUCCESS) r = MsiViewExecute(view, 0);
    if (r != ERROR_SUCCESS) {
        fwprintf(stderr, L"执行失败（%u）：%ls\n", r, sql);
        return false;
    }
    MsiViewClose(view);
    return true;
}

bool HasRows(MSIHANDLE db, const wchar_t* sql, bool& hasRows) {
    PMSIHANDLE view;
    UINT r = MsiDatabaseOpenViewW(db, sql, &view);
    if (r == ERROR_SUCCESS) r = MsiViewExecute(view, 0);
    if (r != ERROR_SUCCESS) {
        fwprintf(stderr, L"查询失败（%u）：%ls\n", r, sql);
        return false;
    }
    PMSIHANDLE record;
    hasRows = MsiViewFetch(view, &record) == ERROR_SUCCESS;
    MsiViewClose(view);
    return true;
}

bool Patch(MSIHANDLE db) {
    // HKMU：按用户安装时落到 HKCU，按机器安装时落到 HKLM
    if (!Execute(db, L"UPDATE `Registry` SET `Root` = -1 WHERE `Root` = 1")) return false;
    // jpackage 的 RemoveFolderEx 会在卸载与升级时整目录删除 INSTALLDIR，连同用户放进去的其他文件
    if (MsiDatabaseIsTablePersistentW(db, L"WixRemoveFolderEx") == MSICONDITION_TRUE &&
        !Execute(db, L"DELETE FROM `WixRemoveFolderEx`")) {
        return false;
    }
    if (!Execute(db, L"DELETE FROM `Property` WHERE `Property` = 'ALLUSERS' OR `Property` = 'MSIINSTALLPERUSER'")) return false;
    if (!Execute(db, L"INSERT INTO `Property` (`Property`, `Value`) VALUES ('ALLUSERS', '2')")) return false;
    if (!Execute(db, L"INSERT INTO `Property` (`Property`, `Value`) VALUES ('MSIINSTALLPERUSER', '1')")) return false;

    bool leftover = true;
    if (!HasRows(db, L"SELECT `Registry` FROM `Registry` WHERE `Root` = 1", leftover)) return false;
    if (leftover) {
        fwprintf(stderr, L"仍有写死 HKCU 的注册表项\n");
        return false;
    }
    return true;
}

}  // namespace

int wmain(int argc, wchar_t** argv) {
    if (argc != 3) {
        fwprintf(stderr, L"用法：msipatch <源 MSI> <输出 MSI>\n");
        return 1;
    }
    const wchar_t* source = argv[1];
    const wchar_t* target = argv[2];
    if (!CopyFileW(source, target, FALSE)) {
        fwprintf(stderr, L"复制 MSI 失败（%lu）\n", GetLastError());
        return 2;
    }
    SetFileAttributesW(target, FILE_ATTRIBUTE_NORMAL);

    MSIHANDLE db = 0;
    UINT r = MsiOpenDatabaseW(target, MSIDBOPEN_TRANSACT, &db);
    if (r != ERROR_SUCCESS) {
        fwprintf(stderr, L"打开 MSI 失败（%u）\n", r);
        DeleteFileW(target);
        return 3;
    }
    bool ok = Patch(db);
    if (ok) {
        r = MsiDatabaseCommit(db);
        if (r != ERROR_SUCCESS) {
            fwprintf(stderr, L"提交 MSI 失败（%u）\n", r);
            ok = false;
        }
    }
    MsiCloseHandle(db);
    if (!ok) {
        DeleteFileW(target);
        return 4;
    }
    return 0;
}
