-- A saved handwritten-signature image per user, mirroring resume_key (feature 09). Uploaded via
-- POST /api/v1/users/me/signature, stored at signatures/{userUuid}/signature.<png|jpg>, and
-- stamped onto offer letters the user e-signs so they don't re-draw it every time.
ALTER TABLE user_profiles
    ADD COLUMN signature_key VARCHAR(255) NULL AFTER resume_key;
