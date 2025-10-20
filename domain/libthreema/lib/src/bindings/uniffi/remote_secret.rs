//! Bindings for the _Remote Secret_.
pub mod setup {
    //! Bindings for the remote secret setup.
    use crate::{
        common::{
            ClientInfo, ThreemaId,
            config::{WorkContext, WorkServerBaseUrl},
            keys::ClientKey,
        },
        remote_secret::setup::{self, RemoteSecretSetupError},
    };

    /// Binding version of [`setup::RemoteSecretSetupContext`].
    #[derive(uniffi::Record)]
    pub struct RemoteSecretSetupContext {
        /// Client info.
        pub client_info: ClientInfo,

        /// Work directory server base URL. Must end with a trailing slash.
        pub work_server_base_url: String,

        /// Work (or OnPrem) application configuration.
        pub work_context: WorkContext,

        /// The user's identity.
        pub user_identity: String,

        /// Client key (32 bytes).
        pub client_key: Vec<u8>,
    }
    impl TryFrom<RemoteSecretSetupContext> for setup::RemoteSecretSetupContext {
        type Error = RemoteSecretSetupError;

        fn try_from(context: RemoteSecretSetupContext) -> Result<Self, Self::Error> {
            let work_server_url = WorkServerBaseUrl::try_from(context.work_server_base_url)
                .map_err(|_| RemoteSecretSetupError::InvalidParameter("'work_server_url' invalid"))?;

            let client_key: [u8; ClientKey::LENGTH] = context
                .client_key
                .try_into()
                .map_err(|_| RemoteSecretSetupError::InvalidParameter("'client_key' must be 32 bytes"))?;

            let user_identity = ThreemaId::try_from(context.user_identity.as_str())
                .map_err(|_| RemoteSecretSetupError::InvalidParameter("'user_identity' invalid"))?;

            Ok(Self {
                client_info: context.client_info,
                work_server_url,
                work_context: context.work_context,
                user_identity,
                client_key: client_key.into(),
            })
        }
    }

    pub mod create {
        //! Bindings for the remote secret creation.
        use std::sync::Mutex;

        use crate::{
            bindings::uniffi::https::HttpsResult,
            common::{ThreemaId, keys::RemoteSecretHash},
            https::HttpsRequest,
            remote_secret::setup::{self, RemoteSecretSetupError, create},
            utils::sync::MutexIgnorePoison as _,
        };

        /// Derive the Remote Secret Hash tied to an identity (RSHID).
        ///
        /// # Errors
        ///
        /// Returns a [`RemoteSecretSetupError::InvalidParameter`] if `remote_secret_hash` is not exactly 32
        /// bytes or `user_identity` is not a valid Threema ID.
        #[uniffi::export]
        pub fn derive_remote_secret_hash_for_identity(
            remote_secret_hash: Vec<u8>,
            user_identity: &str,
        ) -> Result<Vec<u8>, RemoteSecretSetupError> {
            let remote_secret_hash: [u8; RemoteSecretHash::LENGTH] =
                remote_secret_hash.try_into().map_err(|_| {
                    RemoteSecretSetupError::InvalidParameter("'remote_secret_hash' must be 32 bytes")
                })?;

            let user_identity = ThreemaId::try_from(user_identity)
                .map_err(|_| RemoteSecretSetupError::InvalidParameter("'user_identity' invalid"))?;

            Ok(RemoteSecretHash::from(remote_secret_hash)
                .derive_for_identity(user_identity)
                .0
                .to_vec())
        }

        /// Binding version of [`create::RemoteSecretCreateResult`].
        #[derive(uniffi::Record)]
        #[expect(clippy::struct_field_names, reason = "Names as defined in protocol")]
        pub struct RemoteSecretCreateResult {
            /// Established remote secret (32 bytes).
            pub remote_secret: Vec<u8>,

            /// Assigned remote secret authentication token (32 bytes).
            pub remote_secret_authentication_token: Vec<u8>,

            /// The hash derived from the `remote_secret` (32 bytes).
            pub remote_secret_hash: Vec<u8>,
        }

        /// Binding version of [`create::RemoteSecretCreateLoop`].
        #[derive(uniffi::Enum)]
        pub enum RemoteSecretCreateLoop {
            #[expect(missing_docs, reason = "Binding version")]
            Instruction(HttpsRequest),

            #[expect(missing_docs, reason = "Binding version")]
            Done(RemoteSecretCreateResult),
        }
        impl From<create::RemoteSecretCreateLoop> for RemoteSecretCreateLoop {
            fn from(create_loop: create::RemoteSecretCreateLoop) -> Self {
                match create_loop {
                    create::RemoteSecretCreateLoop::Instruction(setup::RemoteSecretSetupInstruction {
                        request,
                    }) => Self::Instruction(request),

                    create::RemoteSecretCreateLoop::Done(result) => Self::Done(RemoteSecretCreateResult {
                        remote_secret: result.remote_secret.0.to_vec(),
                        remote_secret_authentication_token: result
                            .remote_secret_authentication_token
                            .0
                            .to_vec(),
                        remote_secret_hash: result.remote_secret.derive_hash().0.to_vec(),
                    }),
                }
            }
        }

        /// Binding version of [`create::RemoteSecretCreateTask`].
        #[derive(uniffi::Object)]
        pub struct RemoteSecretCreateTask(Mutex<create::RemoteSecretCreateTask>);

        #[uniffi::export]
        impl RemoteSecretCreateTask {
            /// Binding version of [`create::RemoteSecretCreateTask::new`].
            ///
            /// # Errors
            ///
            /// Returns a [`RemoteSecretSetupError::InvalidParameter`] if `context` contains invalid
            /// parameters.
            #[uniffi::constructor]
            pub fn new(context: super::RemoteSecretSetupContext) -> Result<Self, RemoteSecretSetupError> {
                Ok(Self(Mutex::new(create::RemoteSecretCreateTask::new(
                    context.try_into()?,
                ))))
            }

            /// Binding version of [`create::RemoteSecretCreateTask::poll`].
            #[expect(clippy::missing_errors_doc, reason = "Binding version")]
            pub fn poll(&self) -> Result<RemoteSecretCreateLoop, RemoteSecretSetupError> {
                self.0
                    .lock_ignore_poison()
                    .poll()
                    .map(RemoteSecretCreateLoop::from)
            }

            /// Binding version of [`create::RemoteSecretCreateTask::response`].
            #[expect(clippy::missing_errors_doc, reason = "Binding version")]
            pub fn response(&self, response: HttpsResult) -> Result<(), RemoteSecretSetupError> {
                self.0.lock_ignore_poison().response(response.into())
            }
        }
    }

    pub mod delete {
        //! Bindings for the remote secret deletion.
        use std::sync::Mutex;

        use crate::{
            bindings::uniffi::https::HttpsResult,
            common::keys::RemoteSecretAuthenticationToken,
            https::HttpsRequest,
            remote_secret::setup::{self, RemoteSecretSetupError, delete},
            utils::sync::MutexIgnorePoison as _,
        };

        /// Binding version of [`delete::RemoteSecretDeleteLoop`].
        #[derive(uniffi::Enum)]
        pub enum RemoteSecretDeleteLoop {
            #[expect(missing_docs, reason = "Binding version")]
            Instruction(HttpsRequest),

            #[expect(missing_docs, reason = "Binding version")]
            Done,
        }
        impl From<delete::RemoteSecretDeleteLoop> for RemoteSecretDeleteLoop {
            fn from(delete_loop: delete::RemoteSecretDeleteLoop) -> Self {
                match delete_loop {
                    delete::RemoteSecretDeleteLoop::Instruction(setup::RemoteSecretSetupInstruction {
                        request,
                    }) => Self::Instruction(request),

                    delete::RemoteSecretDeleteLoop::Done(()) => Self::Done,
                }
            }
        }

        /// Binding version of [`delete::RemoteSecretDeleteTask`].
        #[derive(uniffi::Object)]
        pub struct RemoteSecretDeleteTask(Mutex<delete::RemoteSecretDeleteTask>);

        #[uniffi::export]
        impl RemoteSecretDeleteTask {
            /// Binding version of [`delete::RemoteSecretDeleteTask::new`].
            ///
            /// # Errors
            ///
            /// Returns a [`RemoteSecretSetupError::InvalidParameter`] if `context` contains invalid
            /// parameters or if the `remote_secret_authentication_token` is not exactly 32 bytes.
            #[uniffi::constructor]
            pub fn new(
                context: super::RemoteSecretSetupContext,
                remote_secret_authentication_token: Vec<u8>,
            ) -> Result<Self, RemoteSecretSetupError> {
                let remote_secret_authentication_token: [u8; RemoteSecretAuthenticationToken::LENGTH] =
                    remote_secret_authentication_token.try_into().map_err(|_| {
                        RemoteSecretSetupError::InvalidParameter(
                            "'remote_secret_authentication_token' must be 32 bytes",
                        )
                    })?;

                Ok(Self(Mutex::new(delete::RemoteSecretDeleteTask::new(
                    context.try_into()?,
                    remote_secret_authentication_token.into(),
                ))))
            }

            /// Binding version of [`delete::RemoteSecretDeleteTask::poll`].
            #[expect(clippy::missing_errors_doc, reason = "Binding version")]
            pub fn poll(&self) -> Result<RemoteSecretDeleteLoop, RemoteSecretSetupError> {
                self.0
                    .lock_ignore_poison()
                    .poll()
                    .map(RemoteSecretDeleteLoop::from)
            }

            /// Binding version of [`delete::RemoteSecretDeleteTask::response`].
            #[expect(clippy::missing_errors_doc, reason = "Binding version")]
            pub fn response(&self, response: HttpsResult) -> Result<(), RemoteSecretSetupError> {
                self.0.lock_ignore_poison().response(response.into())
            }
        }
    }
}

pub mod monitor {
    //! Bindings for the remote secret monitoring.
    use core::time::Duration;
    use std::sync::Mutex;

    use crate::{
        bindings::uniffi::https::HttpsResult,
        common::{
            ClientInfo, ThreemaId,
            config::WorkServerBaseUrl,
            keys::{RemoteSecretAuthenticationToken, RemoteSecretHash, RemoteSecretHashForIdentity},
        },
        https::HttpsRequest,
        remote_secret::monitor::{self, RemoteSecretMonitorError},
        utils::sync::MutexIgnorePoison as _,
    };

    /// Binding version of [`monitor::RemoteSecretVerifier`].
    #[derive(uniffi::Enum)]
    pub enum RemoteSecretVerifier {
        #[expect(missing_docs, reason = "Binding version")]
        RemoteSecretHash(Vec<u8>),

        #[expect(missing_docs, reason = "Binding version")]
        RemoteSecretHashForIdentity {
            user_identity: String,
            remote_secret_hash_for_identity: Vec<u8>,
        },
    }
    impl TryFrom<RemoteSecretVerifier> for monitor::RemoteSecretVerifier {
        type Error = RemoteSecretMonitorError;

        fn try_from(verifier: RemoteSecretVerifier) -> Result<Self, Self::Error> {
            Ok(match verifier {
                RemoteSecretVerifier::RemoteSecretHash(remote_secret_hash) => {
                    let remote_secret_hash: [u8; RemoteSecretHash::LENGTH] =
                        remote_secret_hash.try_into().map_err(|_| {
                            RemoteSecretMonitorError::InvalidParameter(
                                "'remote_secret_hash' must be 32 bytes",
                            )
                        })?;

                    monitor::RemoteSecretVerifier::RemoteSecretHash(remote_secret_hash.into())
                },

                RemoteSecretVerifier::RemoteSecretHashForIdentity {
                    user_identity,
                    remote_secret_hash_for_identity,
                } => {
                    let user_identity = ThreemaId::try_from(user_identity.as_str())
                        .map_err(|_| RemoteSecretMonitorError::InvalidParameter("'user_identity' invalid"))?;

                    let remote_secret_hash_for_identity: [u8; RemoteSecretHashForIdentity::LENGTH] =
                        remote_secret_hash_for_identity.try_into().map_err(|_| {
                            RemoteSecretMonitorError::InvalidParameter(
                                "'remote_secret_hash_for_identity' must be 32 bytes",
                            )
                        })?;

                    monitor::RemoteSecretVerifier::RemoteSecretHashForIdentity {
                        user_identity,
                        remote_secret_hash_for_identity: remote_secret_hash_for_identity.into(),
                    }
                },
            })
        }
    }

    /// Binding version of [`monitor::RemoteSecretMonitorInstruction`].
    #[derive(uniffi::Enum)]
    pub enum RemoteSecretMonitorInstruction {
        #[expect(missing_docs, reason = "Binding version")]
        Request(HttpsRequest),

        #[expect(missing_docs, reason = "Binding version")]
        Schedule {
            timeout: Duration,
            remote_secret: Option<Vec<u8>>,
        },
    }
    impl From<monitor::RemoteSecretMonitorInstruction> for RemoteSecretMonitorInstruction {
        fn from(instruction: monitor::RemoteSecretMonitorInstruction) -> Self {
            match instruction {
                monitor::RemoteSecretMonitorInstruction::Request(https_request) => {
                    Self::Request(https_request)
                },

                monitor::RemoteSecretMonitorInstruction::Schedule {
                    timeout,
                    remote_secret,
                } => Self::Schedule {
                    timeout,
                    remote_secret: remote_secret.map(|remote_secret| remote_secret.0.to_vec()),
                },
            }
        }
    }

    /// Binding version of [`monitor::RemoteSecretMonitorProtocol`].
    #[derive(uniffi::Object)]
    pub struct RemoteSecretMonitorProtocol(Mutex<monitor::RemoteSecretMonitorProtocol>);

    #[uniffi::export]
    impl RemoteSecretMonitorProtocol {
        /// Binding version of [`monitor::RemoteSecretMonitorProtocol::new`].
        ///
        /// # Errors
        ///
        /// Returns a [`RemoteSecretMonitorError::InvalidParameter`] if
        ///
        /// - `work_server_base_url` is not a valid base URL (must end with a trailing slash)
        /// - `remote_secret_authentication_token` is not exactly 32 bytes
        /// - `remote_secret_verifier` contains invalid parameters
        #[uniffi::constructor]
        pub fn new(
            client_info: ClientInfo,
            work_server_base_url: String,
            remote_secret_authentication_token: Vec<u8>,
            remote_secret_verifier: RemoteSecretVerifier,
        ) -> Result<Self, RemoteSecretMonitorError> {
            let work_server_url = WorkServerBaseUrl::try_from(work_server_base_url)
                .map_err(|_| RemoteSecretMonitorError::InvalidParameter("'work_server_base_url' invalid"))?;

            let remote_secret_authentication_token: [u8; RemoteSecretAuthenticationToken::LENGTH] =
                remote_secret_authentication_token.try_into().map_err(|_| {
                    RemoteSecretMonitorError::InvalidParameter(
                        "'remote_secret_authentication_token' must be 32 bytes",
                    )
                })?;

            let remote_secret_verifier = monitor::RemoteSecretVerifier::try_from(remote_secret_verifier)?;

            let context = monitor::RemoteSecretMonitorContext {
                client_info,
                work_server_url,
                remote_secret_authentication_token: remote_secret_authentication_token.into(),
                remote_secret_verifier,
            };

            Ok(Self(Mutex::new(monitor::RemoteSecretMonitorProtocol::new(
                context,
            ))))
        }

        /// Binding version of [`monitor::RemoteSecretMonitorProtocol::poll`].
        #[expect(clippy::missing_errors_doc, reason = "Binding version")]
        pub fn poll(&self) -> Result<RemoteSecretMonitorInstruction, RemoteSecretMonitorError> {
            self.0
                .lock_ignore_poison()
                .poll()
                .map(RemoteSecretMonitorInstruction::from)
        }

        /// Binding version of [`monitor::RemoteSecretMonitorProtocol::response`].
        #[expect(clippy::missing_errors_doc, reason = "Binding version")]
        pub fn response(&self, response: HttpsResult) -> Result<(), RemoteSecretMonitorError> {
            self.0.lock_ignore_poison().response(response.into())
        }
    }
}
