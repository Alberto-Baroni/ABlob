# Microsoft Entra ID in ABlob

ABlob 1.1.0 uses delegated Microsoft sign-in through MSAL Android, the system browser and Authorization Code with PKCE. It does not request, save or send a client secret. MSAL manages its protected token cache and silent renewal. Existing Entra connections must sign in again after upgrading; their old stored client secret is removed.

## Use your own Azure environment

The tenant, client ID, storage account and container are entered by the user. ABlob does not use the developer's Azure tenant or storage account for this flow. A Microsoft work or school account must have access to the selected storage container.

1. In your tenant, create or select a Microsoft Entra app registration intended for a **public mobile client**. Record the Directory (tenant) ID and Application (client) ID. Do not create a client secret.
2. Install the ABlob build you will use. In the Entra ID tab, copy the displayed redirect URI. It includes the package `com.alkaloon.ablob` and the signature hash of the installed app.
3. In the app registration's **Authentication** settings, add the Android platform with that package and signature hash. The signature hash is the final component of the displayed URI, URL-decoded (for example, `%2B` becomes `+`, `%2F` becomes `/` and `%3D` becomes `=`). Check that the resulting redirect URI matches the one shown by ABlob. The registration must accept the native public-client authorization code flow.
4. Under API permissions, add **Azure Storage → Delegated permissions → user_impersonation**. An administrator may need to grant consent according to your organization's policy.
5. In the selected storage account/container's **Access control (IAM)**, assign the signing-in user or an appropriate group **Storage Blob Data Reader** for browsing and downloading, or **Storage Blob Data Contributor** for uploading, renaming and deleting test files. Management roles such as Contributor alone do not grant Blob data access. Assign permissions at the narrowest suitable scope and allow time for propagation.
6. In ABlob, enter the storage account name, container name, tenant ID and client ID, then choose **Sign in with Microsoft**. Select your account and complete any required MFA or consent. ABlob checks access to the container before saving the connection.

For a user from another organization, arrange guest access and permissions in the storage tenant, or a suitable organizational multi-tenant app/consent configuration. Supplying an arbitrary tenant or client ID does not grant access by itself.

## Signing and Google Play

Debug, locally signed release and Google Play builds can have different signing certificates. Register the redirect URI for each build you use. **Play App Signing's application signing certificate can differ from the upload key.** The URI displayed in the installed Play build uses the Play signing certificate automatically.

The manifest accepts the `msauth` callback for this application's package; MSAL validates the response and owns the PKCE/state handling. Tokens are attached only to the configured storage account's HTTPS endpoint.

## Session and errors

- On reopening ABlob, MSAL attempts to obtain a token silently for the saved account, tenant and client ID.
- If consent, MFA or sign-in is required again, reconnect through **Settings → Edit credentials**.
- **Disconnect** removes the selected account from this application's MSAL cache before clearing the saved storage connection. It does not sign the user out of the system browser or revoke their organization's credentials.
- A successful Microsoft login followed by Blob HTTP 403 usually requires checking the user's Blob data roles, storage firewall and tenant/storage selection.
- SAS and Shared Key authentication remain separate options.

## References

- [MSAL Android configuration](https://learn.microsoft.com/en-us/entra/msal/android/msal-configuration)
- [MSAL Android redirect URI and signature hash](https://learn.microsoft.com/en-us/entra/msal/android/frequently-asked-questions)
- [Delegated access to Blob Storage with Microsoft Entra ID](https://learn.microsoft.com/en-us/azure/storage/blobs/authorize-access-azure-active-directory)

## Verification

Unit tests cover public-client configuration, tenant/client validation, delegated Bearer attachment, changes of connection, authentication failure and credential scope. Device tests cover MSAL initialization, Android callback resolution and migration of legacy encrypted preferences. A full interactive login requires an actual customer app registration, user consent and Blob data permissions and must also be exercised before production rollout.
