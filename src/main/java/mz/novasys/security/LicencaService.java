package mz.novasys.security;

import java.security.MessageDigest;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.prefs.Preferences;

public class LicencaService {

    private static final String MASTER_MENSAL     = "NOVASYS-MASTER-MENSAL-2026";
    private static final String MASTER_VITALICIO  = "NOVASYS-MASTER-VITALICIO-2026";

    private static final Preferences prefs = Preferences.userNodeForPackage(LicencaService.class);

    /**
     * Retorna a impressão digital única do hardware (Hardware UUID baseada no MAC/Rede)
     */
    public static String getHardwareUUID() {
        try {
            StringBuilder sb = new StringBuilder();
            Enumeration<NetworkInterface> networks = NetworkInterface.getNetworkInterfaces();
            while (networks.hasMoreElements()) {
                NetworkInterface network = networks.nextElement();
                byte[] mac = network.getHardwareAddress();
                if (mac != null) {
                    for (int i = 0; i < mac.length; i++) {
                        sb.append(String.format("%02X", mac[i]));
                    }
                    break;
                }
            }
            if (sb.length() == 0) {
                return "1234-ABCD-5678-EFGH";
            }
            String macStr = sb.toString();
            return macStr.length() >= 16 ? macStr.substring(0, 16) : macStr;
        } catch (Exception e) {
            return "DEFAULT-UUID-NOVASYS";
        }
    }

    /**
     * Valida a chave inserida (seja chave mestre ou chave específica do PC)
     */
    public static boolean validarAtivacao(String chaveInserida, String hardwareUuid) {
        if (chaveInserida == null || chaveInserida.trim().isEmpty()) {
            return false;
        }

        String chaveLimpa = chaveInserida.trim().toUpperCase();

        // 1. Validar Chaves Mestras
        if (chaveLimpa.equals(MASTER_MENSAL) || chaveLimpa.equals(MASTER_VITALICIO)) {
            prefs.put("CHAVE_REGISTADA", chaveLimpa);
            prefs.put("TIPO_LICENCA", chaveLimpa.contains("VITALICIO") ? "VITALICIO" : "MENSAL");
            return true;
        }

        // 2. Validar Chaves por Hardware UUID
        String chaveValidaMensal = gerarChaveCliente(hardwareUuid, false);
        String chaveValidaVitalicio = gerarchaveCliente(hardwareUuid, true);

        if (chaveLimpa.equals(chaveValidaMensal) || chaveLimpa.equals(chaveValidaVitalicio)) {
            prefs.put("CHAVE_REGISTADA", chaveLimpa);
            prefs.put("TIPO_LICENCA", chaveLimpa.equals(chaveValidaVitalicio) ? "VITALICIO" : "MENSAL");
            return true;
        }

        return false;
    }

    private static String gerarchaveCliente(String uuid, boolean vit) {
        return gerarChaveCliente(uuid, vit);
    }

    /**
     * Renova a licença com uma nova chave fornecida
     */
    public static boolean renovarLicenca(String novaChave) {
        String uuid = getHardwareUUID();
        return validarAtivacao(novaChave, uuid);
    }

    /**
     * Retorna os dias restantes da licença atual
     */
    public static int verificarDiasRestantes() {
        String tipo = prefs.get("TIPO_LICENCA", "");
        if (tipo.equals("VITALICIO") || tipo.contains("VITALICIO")) {
            return 9999; // Vitalício (ilimitado)
        }
        if (tipo.equals("MENSAL") || tipo.contains("MENSAL")) {
            return 30; // Padrão mensal
        }
        return 0;
    }

    /**
     * Gera matematicamente a chave vinculada ao UUID
     */
    public static String gerarChaveCliente(String hardwareUuid, boolean vitalicio) {
        try {
            String tipo = vitalicio ? "VITALICIO" : "MENSAL";
            String rawData = hardwareUuid + "-" + tipo + "-NOVASYS-SALT-2026";
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawData.getBytes("UTF-8"));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            String fullHash = hexString.toString().toUpperCase();
            return fullHash.substring(0, 4) + "-" + 
                   fullHash.substring(4, 8) + "-" + 
                   fullHash.substring(8, 12) + "-" + 
                   fullHash.substring(12, 16);
                   
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean isLicenciado() {
        String registada = prefs.get("CHAVE_REGISTADA", "");
        return !registada.isEmpty();
    }
}