import { createContext, useContext } from 'react';
import { api, Api } from './client';

const ApiContext = createContext<Api>(api);

export const ApiProvider = ApiContext.Provider;

export function useApi(): Api {
  return useContext(ApiContext);
}
