package net.ogatomo.developerOptions;

/**
 * Shizuku UserService 上で shell 権限としてコマンドを実行する。
 * destroy の transaction code は Shizuku 仕様 (16777114 in AIDL)。
 */
interface IShellCommandService {
    /**
     * @return 先頭行が終了コード、以降が stdout+stderr を結合した文字列
     */
    String exec(String command) = 1;

    void destroy() = 16777114;
}
