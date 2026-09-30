import { language } from './i18n';
export class ApiError extends Error {constructor(public status:number,message:string){super(message);}}
// Always same-origin and root-relative: locale URLs never change the API prefix.
export async function apiRequest<T>(path:string,method='GET',body?:unknown):Promise<T> {
  if(!path.startsWith('/api/'))throw new Error('API paths must start with /api/');
  let response:Response;
  try {response=await fetch(path,{method,credentials:'same-origin',headers:{'Content-Type':'application/json','X-Requested-With':'changoff','Accept-Language':language},body:body===undefined?undefined:JSON.stringify(body)});}catch{throw new ApiError(0,'Unable to connect. Check your connection and try again.');}
  if(!response.ok){const data=await response.json().catch(()=>({message:'Request failed. Please try again.'}));throw new ApiError(response.status,data.message||'Request failed. Please try again.');}
  return response.status===204?undefined as T:response.json();
}
