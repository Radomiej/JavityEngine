const api = globalThis.JavityReact;
if (!api?.React) throw new Error('Load the Javity React runtime bundle before the sample app');

export const JavityReact = api;
export const React = api.React;
export const useLayoutEffect = React.useLayoutEffect;
export const useEffect = React.useEffect;
export const useState = React.useState;
export const useRef = React.useRef;
export const useMemo = React.useMemo;
export const useCallback = React.useCallback;
export const useContext = React.useContext;
export const createContext = React.createContext;

/** Stable callback for an explicitly registered primitive JavityGame action. */
export function useAction(name) {
  return React.useCallback((...args) => {
    JavityGame.call(name, ...args);
  }, [name]);
}

export function useSignal(name) {
  const [value, setValue] = useState(() => JavityGame.get(name));
  useEffect(() => {
    setValue(JavityGame.get(name));
    const id = JavityGame.subscribe(name, setValue);
    return () => JavityGame.unsubscribe(id);
  }, [name]);
  return value;
}
