/*
 * Copyright (c) 2024 ModCore Inc. All rights reserved.
 *
 * This code is part of ModCore Inc.'s Essential Mod repository and is protected
 * under copyright registration # TX0009138511. For the full license, see:
 * https://github.com/EssentialGG/Essential/blob/main/LICENSE
 *
 * You may not use, copy, reproduce, modify, sell, license, distribute,
 * commercialize, or otherwise exploit, or create derivative works based
 * upon, this file or any other in this repository, all of which is reserved by Essential.
 */
package gg.essential.util;

public class BuildInfo {
    public static final int TARGET_MC_VERSION =
        //#if MC == 1.8.9
        //$$ 10809;
        //#elseif MC == 1.12.2
        11202;
        //#elseif MC == 1.16.5
        //$$ 11605;
        //#elseif MC == 1.17.1
        //$$ 11701;
        //#elseif MC == 1.18.1
        //$$ 11801;
        //#elseif MC == 1.18.2
        //$$ 11802;
        //#elseif MC == 1.19
        //$$ 11900;
        //#elseif MC == 1.19.2
        //$$ 11902;
        //#elseif MC == 1.19.3
        //$$ 11903;
        //#elseif MC == 1.19.4
        //$$ 11904;
        //#elseif MC == 1.20
        //$$ 12000;
        //#elseif MC == 1.20.1
        //$$ 12001;
        //#elseif MC == 1.20.2
        //$$ 12002;
        //#elseif MC == 1.20.4
        //$$ 12004;
        //#elseif MC == 1.20.6
        //$$ 12006;
        //#elseif MC == 1.21.1
        //$$ 12101;
        //#elseif MC == 1.21.3
        //$$ 12103;
        //#elseif MC == 1.21.4
        //$$ 12104;
        //#elseif MC == 1.21.5
        //$$ 12105;
        //#elseif MC == 1.21.6
        //$$ 12106;
        //#elseif MC == 1.21.7
        //$$ 12107;
        //#elseif MC == 1.21.9
        //$$ 12109;
        //#elseif MC == 1.21.11
        //$$ 12111;
        //#elseif MC == 26.1.2
        //$$ 26_01_02;
        //#elseif MC == 26.2
        //$$ 26_02_00;
        //#elseif MC == 26.3
        //$$ 26_03_00;
        //#else
        //$$ ADD_CASE_FOR_NEW_VERSION_TO_ABOVE_LIST;
        //#endif
}
