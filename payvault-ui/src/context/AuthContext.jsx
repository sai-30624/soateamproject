import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import api, { tokenStore } from '../api.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [tokens, setTokens] = useState(tokenStore.get())
  const [user, setUser] = useState(null)

  const loadProfile = useCallback(async () => {
    try {
      const { data } = await api.get('/users/me')
      setUser(data)
    } catch {
      /* handled by interceptor */
    }
  }, [])

  useEffect(() => {
    if (tokens) loadProfile()
  }, [tokens, loadProfile])

  const save = (data) => {
    tokenStore.set(data)
    setTokens(data)
  }

  const login = async (email, password) => {
    const { data } = await api.post('/auth/login', { email, password })
    save(data)
  }

  const register = async (form) => {
    const { data } = await api.post('/auth/register', form)
    save(data)
  }

  const logout = () => {
    tokenStore.clear()
    setTokens(null)
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ isLoggedIn: !!tokens, user, setUser, login, register, logout, reloadUser: loadProfile }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)
