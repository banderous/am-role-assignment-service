-- Postgres Function to insert bulk assignment records
drop type if exists roleCategories;
create type roleCategories as enum('PROFESSIONAL', 'JUDICIAL','LEGAL_OPERATIONS');

drop type if exists professionalRoles;
create type professionalRoles as enum('petitioner''s-solicitor', 'respondent''s-solicitor','ls-solicitor', 'legal-representative', 'barrister', 'cafcass-solicitor', 'external-professional-solicitor', 'la-barrister',
                          'managing-local-authority', 'solicitor', 'solicitor-a', 'solicitor-b',
                          'solicitor-c', 'solicitor-d', 'solicitor-e', 'solicitor-f', 'solicitor-g', 'solicitor-h',
                          'solicitor-i', 'solicitor-j');

drop type if exists staffRoles;
create type staffRoles as enum('tribunal-caseworker');

--create function insert_bulk_assignments() RETURNS void AS $$
CREATE or replace FUNCTION insert_bulk_assignments()
RETURNS void AS $$
declare
	assignmentId uuid;
	actorId uuid;
	number_of_assignments INT;
	professionalRole text;
	staffRole text;
	roleCategory text;
	finalRole text;
begin
	--300000
	FOR counter IN 1..3
loop
	--Generate random UUID for Actor
	actorId := uuid_in(md5(random()::text || clock_timestamp()::text)::cstring);
	begin
			--Generate a number between 1-30
			SELECT floor(random()*30)+1 into number_of_assignments;

			--Fetch random Role Category
			SELECT roleName into roleCategory FROM ( SELECT unnest(enum_range(NULL::roleCategories)) as roleName ) sub ORDER BY random() LIMIT 1;

			--Fetch random role from enum for Professional category
			SELECT roleName into professionalRole FROM ( SELECT unnest(enum_range(NULL::professionalRoles)) as roleName ) sub ORDER BY random() LIMIT 1;

			--Fetch random role from enum for Staff category
			SELECT roleName into staffRole FROM ( SELECT unnest(enum_range(NULL::staffRoles)) as roleName ) sub ORDER BY random() LIMIT 1;


			IF roleCategory = 'PROFESSIONAL'
			THEN
				finalRole = professionalRole;
			END IF;

			IF roleCategory = 'JUDICIAL'
			THEN
				finalRole = 'judge';
			END IF;

			IF roleCategory = 'LEGAL_OPERATIONS'
			THEN
				finalRole = staffRole;
			END IF;


			FOR counter IN 1..number_of_assignments
		    loop
		    	--Generate random UUID for assignmentId
		    	assignmentId := uuid_in(md5(random()::text || clock_timestamp()::text)::cstring);
		        INSERT INTO public.role_assignment
				(id, actor_id_type, actor_id, role_type, role_name, classification, grant_type, role_category, read_only, begin_time, end_time, "attributes", created)
				VALUES(assignmentId, 'IDAM', actorId, 'CASE', finalRole, 'RESTRICTED', 'SPECIFIC', roleCategory, false, '2021-08-01 00:00:00.000', null, '{"caseId": "1234567890123456", "caseType": "Asylum", "jurisdiction": "IA"}', '2020-07-26 23:39:13.835');
		    end loop;
	end;
	-- Insert actor cache records
	INSERT INTO public.actor_cache_control
	(actor_id, etag, json_response)
	VALUES(actorId, 0, '[{}]');


END LOOP;
exception when others then
	begin

	end;
end;
$$ LANGUAGE plpgsql;

--Invoke Function
--select insert_bulk_assignments();
--select count(*) FROM role_assignment; --11103 --11131-- 4,668,640
--select count(*) from actor_cache_control; --7947 --7950 -- 307,950
