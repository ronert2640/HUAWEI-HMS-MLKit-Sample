/**
 * Copyright 2020. Huawei Technologies Co., Ltd. All rights reserved.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.mlkit.sample.activity;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.mlkit.sample.R;
import com.mlkit.sample.util.Constant;
import com.huawei.hms.mlsdk.common.internal.client.SmartLog;

public class SettingActivity extends BaseActivity implements View.OnClickListener {
    private static final String TAG = "SettingActivity";

    private TextView mVersion;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.activity_setting);
        this.mVersion = this.findViewById(R.id.version);
        this.mVersion.setText(this.getVersionName());
        this.findViewById(R.id.back).setOnClickListener(this);
    }


    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.back:
                this.finish();
                break;
            default:
                break;
        }
    }

    /**
     * get App versionName
     *
     * @return
     */
    public String getVersionName() {
        PackageManager packageManager = this.getPackageManager();
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(this.getPackageName(), 0);
            String mVersionName = packageInfo.versionName;
            return mVersionName;
        } catch (PackageManager.NameNotFoundException e) {
            SmartLog.e(SettingActivity.TAG, "Failed to get package version: " + e.getMessage());
        }
        return Constant.DEFAULT_VERSION;
    }
}
