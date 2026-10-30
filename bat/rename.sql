-- rename companies (company_id keeps all links)
UPDATE public.companies SET company_name='S봇 통운', representative_name='최길웅' WHERE company_id=5;
UPDATE public.companies SET company_name='S카고 익스프레스', representative_name='한솔' WHERE company_id=10;
UPDATE public.companies SET company_name='태수앤S봇 물류' WHERE company_id=13;
SELECT company_id, representative_name, company_name FROM public.companies WHERE company_id IN (5,10,13) ORDER BY company_id;
