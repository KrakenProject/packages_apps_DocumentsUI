/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.documentsui;

import android.annotation.Nullable;
import android.app.Activity;
import android.net.Uri;
import android.provider.DocumentsContract.Path;
import android.util.Log;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.PairedTask;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.roots.RootsAccess;

import java.util.List;

/**
 * Loads {@link DocumentStack} for given document. It provides its best effort to find the path of
 * the given document.
 *
 * If it fails to load correct path it calls callback with different result
 * depending on the nullness of given root. If given root is null it calls callback with null. If
 * given root is not null it calls callback with a {@link DocumentStack} as if the given doc lives
 * under the root doc.
 */
public class LoadDocStackTask extends PairedTask<Activity, Void, DocumentStack> {
    private static final String TAG = "LoadDocStackTask";

    private final RootsAccess mRoots;
    private final DocumentsAccess mDocs;
    private final Uri mDocUri;
    private final String mAuthority;
    private final ProviderAccess mProviders;
    private final LoadDocStackCallback mCallback;

    public LoadDocStackTask(
            Activity activity,
            Uri docUri,
            RootsAccess roots,
            DocumentsAccess docs,
            ProviderAccess providers,
            LoadDocStackCallback callback) {
        super(activity);
        mRoots = roots;
        mDocs = docs;
        mDocUri = docUri;
        mAuthority = docUri.getAuthority();
        mProviders = providers;
        mCallback = callback;
    }

    @Override
    public @Nullable DocumentStack run(Void... args) {
        if (Shared.ENABLE_OMC_API_FEATURES) {
            try {
                final Path path = mProviders.findPath(mDocUri);
                if (path != null) {
                    return buildStack(path);
                } else {
                    Log.i(TAG, "Remote provider doesn't support findPath.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to build document stack for uri: " + mDocUri, e);
                // Fallback to old behavior.
            }
        }

        return null;
    }

    @Override
    public void finish(@Nullable DocumentStack stack){
        mCallback.onDocumentStackLoaded(stack);
    }

    private @Nullable DocumentStack buildStack(Path path) {
        final String rootId = path.getRootId();
        if (rootId == null) {
            Log.e(TAG, "Provider doesn't provide root id.");
            return null;
        }

        RootInfo root = mRoots.getRootOneshot(mAuthority, path.getRootId());
        List<DocumentInfo> docs = mDocs.getDocuments(mAuthority, path.getPath());

        if (root == null || docs == null) {
            Log.e(TAG, "Either root: " + root + " or docs: " + docs + " failed to load.");
            return null;
        }

        return new DocumentStack(root, docs);
    }

    @FunctionalInterface
    public interface LoadDocStackCallback {
        void onDocumentStackLoaded(@Nullable DocumentStack stack);
    }
}