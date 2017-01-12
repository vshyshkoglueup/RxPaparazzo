/*
 * Copyright 2016 Miguel Garcia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.miguelbcr.ui.rx_paparazzo2.workers;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import com.miguelbcr.ui.rx_paparazzo2.entities.Config;
import com.miguelbcr.ui.rx_paparazzo2.entities.FileData;
import com.miguelbcr.ui.rx_paparazzo2.entities.Ignore;
import com.miguelbcr.ui.rx_paparazzo2.entities.Response;
import com.miguelbcr.ui.rx_paparazzo2.entities.TargetUi;
import com.miguelbcr.ui.rx_paparazzo2.interactors.CropImage;
import com.miguelbcr.ui.rx_paparazzo2.interactors.GetPath;
import com.miguelbcr.ui.rx_paparazzo2.interactors.GrantPermissions;
import com.miguelbcr.ui.rx_paparazzo2.interactors.SaveImage;
import com.miguelbcr.ui.rx_paparazzo2.interactors.TakePhoto;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.functions.Function;

public final class Camera extends Worker {
  private final TakePhoto takePhoto;
  private final CropImage cropImage;
  private final SaveImage saveImage;
  private final GrantPermissions grantPermissions;
  private final TargetUi targetUi;
  private final Config config;
  private final GetPath getPath;

  public Camera(TakePhoto takePhoto, GetPath getPath, CropImage cropImage, SaveImage saveImage,
      GrantPermissions grantPermissions, TargetUi targetUi, Config config) {
    super(targetUi);
    this.takePhoto = takePhoto;
    this.getPath = getPath;
    this.cropImage = cropImage;
    this.saveImage = saveImage;
    this.grantPermissions = grantPermissions;
    this.targetUi = targetUi;
    this.config = config;
  }

  public <T> Observable<Response<T, FileData>> takePhoto() {
    return grantPermissions.with(permissions())
        .react()
        .flatMap(new Function<Ignore, ObservableSource<Uri>>() {
          @Override public ObservableSource<Uri> apply(Ignore ignore) throws Exception {
            return takePhoto.react();
          }
        })
        .flatMap(new Function<Uri, ObservableSource<FileData>>() {
          @Override
          public ObservableSource<FileData> apply(final Uri uri) throws Exception {
            return getPath.with(uri).react();
          }
        })
        .flatMap(new Function<FileData, ObservableSource<FileData>>() {
          @Override public ObservableSource<FileData> apply(FileData fileData) throws Exception {
            return handleSavingFile(fileData);
          }
        })
        .flatMap(new Function<FileData, ObservableSource<FileData>>() {
          @Override public ObservableSource<FileData> apply(FileData fileData) throws Exception {
            return saveImage.with(fileData).react();
          }
        })
        .map(new Function<FileData, Response<T, FileData>>() {
          @Override public Response<T, FileData> apply(FileData fileData) throws Exception {
            return new Response<>((T) targetUi.ui(), fileData, Activity.RESULT_OK);
          }
        })
        .compose(this.<Response<T, FileData>>applyOnError());
  }

  private Observable<FileData> handleSavingFile(final FileData sourceFileData) {
    return cropImage.with(sourceFileData).react()
            .flatMap(new Function<Uri, ObservableSource<FileData>>() {
              @Override
              public ObservableSource<FileData> apply(Uri uri) throws Exception {
                return getPath.with(uri).react().flatMap(new Function<FileData, ObservableSource<FileData>>() {
                  @Override
                  public ObservableSource<FileData> apply(FileData cropped) throws Exception {
                    FileData destination = new FileData(cropped.getFile(), sourceFileData.getFilename());

                    return saveImage.with(destination).react();
                  }
                });
              }
            });
  }

  private String[] permissions() {
    if (config.useInternalStorage()) {
      if (hasCameraPermissionInManifest()) {
        return new String[] { Manifest.permission.CAMERA };
      } else {
        return new String[] {};
      }
    } else {
      if (hasCameraPermissionInManifest()) {
        return new String[] {
            Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        };
      } else {
        return new String[] {
            Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE
        };
      }
    }
  }

  private boolean hasCameraPermissionInManifest() {
    final String packageName = targetUi.getContext().getPackageName();
    try {
      final PackageInfo packageInfo = targetUi.getContext()
          .getPackageManager()
          .getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
      final String[] declaredPermissions = packageInfo.requestedPermissions;
      if (declaredPermissions != null && declaredPermissions.length > 0) {
        for (String p : declaredPermissions) {
          if (p.equals(Manifest.permission.CAMERA)) {
            return true;
          }
        }
      }
    } catch (PackageManager.NameNotFoundException e) { /*Nothing*/ }

    return false;
  }
}
