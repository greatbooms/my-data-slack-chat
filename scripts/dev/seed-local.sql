-- Local development seed data.
-- Run after the application has started once so AdminBootstrapInitializer can create the admin user/workspace.
\set ON_ERROR_STOP on

BEGIN;

DO $$
DECLARE
    v_admin_id UUID;
    v_workspace_id UUID;
    v_notion_source_id UUID;
BEGIN
    SELECT id
      INTO v_admin_id
      FROM users
     WHERE email = 'admin@example.com'
       AND deleted_at IS NULL
     ORDER BY created_at
     LIMIT 1;

    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'admin@example.com 사용자를 찾지 못했습니다. 먼저 애플리케이션을 한 번 시작해 bootstrap을 실행하세요.';
    END IF;

    SELECT id
      INTO v_workspace_id
      FROM workspaces
     WHERE owner_user_id = v_admin_id
       AND deleted_at IS NULL
     ORDER BY created_at
     LIMIT 1;

    IF v_workspace_id IS NULL THEN
        INSERT INTO workspaces (owner_user_id, name)
        VALUES (v_admin_id, 'Personal')
        RETURNING id INTO v_workspace_id;
    END IF;

    INSERT INTO external_identities (
        user_id,
        workspace_id,
        provider,
        external_workspace_id,
        external_user_id,
        email,
        display_name,
        principal_key
    )
    VALUES (
        v_admin_id,
        v_workspace_id,
        'SLACK',
        'T08NU5WV3T5',
        'U08NU5WV5B5',
        'greatbooms109@gmail.com',
        'eric-private-eric',
        'SLACK_USER:T08NU5WV3T5:U08NU5WV5B5'
    )
    ON CONFLICT (provider, external_workspace_id, external_user_id)
    DO UPDATE SET
        user_id = EXCLUDED.user_id,
        workspace_id = EXCLUDED.workspace_id,
        email = EXCLUDED.email,
        display_name = EXCLUDED.display_name,
        principal_key = EXCLUDED.principal_key;

    SELECT id
      INTO v_notion_source_id
      FROM data_sources
     WHERE type = 'NOTION'
       AND name = 'notion-test'
     ORDER BY created_at
     LIMIT 1;

    IF v_notion_source_id IS NULL THEN
        INSERT INTO data_sources (
            workspace_id,
            owner_user_id,
            type,
            name,
            status,
            sync_mode,
            visibility,
            config_json
        )
        VALUES (
            v_workspace_id,
            v_admin_id,
            'NOTION',
            'notion-test',
            'ACTIVE',
            'MANUAL',
            'PRIVATE',
            '{"notionRootPageId": "38b4f8f585ae808ea417c160a0739799"}'::jsonb
        )
        RETURNING id INTO v_notion_source_id;
    ELSE
        UPDATE data_sources
           SET workspace_id = v_workspace_id,
               owner_user_id = v_admin_id,
               status = 'ACTIVE',
               sync_mode = 'MANUAL',
               visibility = 'PRIVATE',
               deleted_at = NULL,
               config_json = '{"notionRootPageId": "38b4f8f585ae808ea417c160a0739799"}'::jsonb,
               updated_at = now()
         WHERE id = v_notion_source_id;
    END IF;

    DELETE FROM data_source_access_policies
     WHERE data_source_id = v_notion_source_id;

    INSERT INTO data_source_access_policies (data_source_id, principal_key, permission)
    VALUES (v_notion_source_id, 'USER:' || v_admin_id, 'READ')
    ON CONFLICT (data_source_id, principal_key, permission) DO NOTHING;
END $$;

COMMIT;
