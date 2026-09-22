alter table customer_profiles add column referral_code varchar(32) null;
alter table buildings add column building_preference varchar(20) null;

update lifts set lift_number = null where lift_number is not null and trim(lift_number) = '';

update lifts l
set lift_number = null
where l.lift_number is not null
  and exists (
    select 1
    from (
      select older.lift_number, older.id
      from lifts older
    ) older_lifts
    where older_lifts.lift_number = l.lift_number
      and older_lifts.id < l.id
  );

create unique index uk_customer_profiles_referral_code on customer_profiles(referral_code);
create unique index uk_lifts_lift_number on lifts(lift_number);
