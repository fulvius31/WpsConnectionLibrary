#ifndef PIXIEWPS_WRAPPER_H
#define PIXIEWPS_WRAPPER_H

/**
 * Compute WPS PIN using the Pixie Dust attack.
 *
 * Wraps the pixiewps algorithm. This is a pure computation that
 * does NOT require root privileges.
 *
 * @param pke       DH own Public Key (hex string)
 * @param pkr       DH peer Public Key (hex string)
 * @param e_hash1   E-Hash1 (hex string)
 * @param e_hash2   E-Hash2 (hex string)
 * @param auth_key  AuthKey (hex string)
 * @param e_nonce   Enrollee Nonce (hex string)
 * @param force     Use --force mode
 * @param pin_out   Output buffer for the computed PIN
 * @param pin_size  Size of pin_out buffer
 * @return          0 on success (PIN found), -1 on failure (not vulnerable)
 */
int pixiewps_compute(const char *pke, const char *pkr,
                      const char *e_hash1, const char *e_hash2,
                      const char *auth_key, const char *e_nonce,
                      int force, char *pin_out, int pin_size);

#endif // PIXIEWPS_WRAPPER_H
