/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.config;

/**
 *
 * @author Paolo Domenighetti
 */
public class OdbConfFilePrinter {

    private static void printGeneral(StringBuilder buffer, String swapFolder, int swapThresholdMB, String odbInternalLogPath) {
        if (odbInternalLogPath.endsWith("/")) {
            odbInternalLogPath = odbInternalLogPath.substring(0, odbInternalLogPath.length() - 1);
        }
        buffer.append("	<general>\n"
                + "		<temp path=\"" + swapFolder + "\" threshold=\"" + swapThresholdMB + "mb\" />\n"
                + "		<network inactivity-timeout=\"0\" />\n"
                + "		<url-history size=\"50\" user=\"true\" password=\"true\" />\n"
                + "		<log path=\"" + odbInternalLogPath + "\" max=\"8mb\" stdout=\"false\" stderr=\"false\" />\n"
                + "		<log-archive path=\"" + odbInternalLogPath + "/archive/\" retain=\"90\" />\n"
                + "		<logger name=\"*\" level=\"info\" />\n"
                + "	</general>\n\n");
    }

    private static void printDatabase(StringBuilder buffer, int performanceMod, String recoveryPath, String recordingPath) {
        buffer.append("<database>\n"
                + "		<size initial=\"256kb\" resize=\"256kb\" page=\"2kb\" />\n"
                + "		<recovery enabled=\"true\" sync=\"false\" path=\".\" max=\"128mb\" />\n"
                + "		<recording enabled=\"false\" sync=\"false\" path=\".\" mode=\"write\" />\n"
                + "		<locking version-check=\"true\" />\n"
                + "		<processing cache=\"64mb\" max-threads=\"10\" /> \n"
                + "		<query-cache results=\"32mb\" programs=\"500\" />\n"
                + "		<extensions drop=\"temp,tmp\" memory=\"mem\" />\n"
                + "	</database>\n\n");
    }

    private static void printEntities(StringBuilder buffer, int performanceMod) {
        buffer.append("<entities>\n"
                + "		<enhancement agent=\"false\" reflection=\"warning\" />\n"
                + "		<cache ref=\"weak\" level2=\"0\" />\n"
                + "		<persist serialization=\"false\" />\n"
                + "		<cascade-persist always=\"auto\" on-persist=\"false\" on-commit=\"true\" />\n"
                + "		<dirty-tracking arrays=\"false\" />\n"
                + "	</entities>\n\n");
    }

    private static void printServer(StringBuilder buffer, int port, String odbDataPath) {
        buffer.append("	<server>\n"
                + "		<connection port=\"6136\" max=\"0\" />\n"
                + "		<data path=\"$objectdb/db\" />\n"
                + "		<!--\n"
                + "		<replication url=\"objectdb://localhost/test.odb;user=admin;password=admin\" />\n"
                + "		-->\n"
                + "	</server>\n\n");
    }

    private static void printUsers(StringBuilder buffer) {
        buffer.append("	<users>\n"
                + "		<user username=\"admin\" password=\"admin\">\n"
                + "			<dir path=\"/\" permissions=\"access,modify,create,delete\" />\n"
                + "		</user>\n"
                + "		<user username=\"$default\" password=\"$$$###\">\n"
                + "			<dir path=\"/$user/\" permissions=\"access,modify,create,delete\">\n"
                + "				<quota directories=\"5\" files=\"20\" disk-space=\"5mb\" />\n"
                + "			</dir>\n"
                + "		</user>\n"
                + "		<user username=\"user1\" password=\"user1\" />\n"
                + "	</users>\n\n");
    }

    private static void printSsl(StringBuilder buffer) {
        buffer.append("<ssl enabled=\"false\">\n"
                + "		<server-keystore path=\"$objectdb/ssl/server-kstore\" password=\"pwd\" />\n"
                + "		<client-truststore path=\"$objectdb/ssl/client-tstore\" password=\"pwd\" />\n"
                + "	</ssl>");
    }

    public static void buildConf() {
        StringBuilder sb = new StringBuilder();
        sb.append("<objectdb>\n\n");
//        printGeneral(sb);
//        printDatabase(sb);
//        printEntities(sb);
//        sb.append("	<schema>\n"
//                + "	</schema>\n");
//        printServer(sb);
//        printUsers(sb);
        printSsl(sb);
        sb.append("</objectdb>\n");
    }

}
