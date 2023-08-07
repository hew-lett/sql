//package main.app;
//
//import com.jcraft.jsch.ChannelSftp;
//import com.jcraft.jsch.JSch;
//import com.jcraft.jsch.Session;
//import com.jcraft.jsch.SftpException;
//
//public class SFTPDownloader {
//
//    public static void main(String[] args) {
//        String user = "username";
//        String host = "hostname";
//        int port = 22; //Default SFTP port
//        String password = "password";
//        String remoteFile = "/path/to/remote/file.txt";
//        String localDir = "/path/to/local/directory/";
//
//        try {
//            JSch jsch = new JSch();
//            Session session = jsch.getSession(user, host, port);
//            session.setConfig("StrictHostKeyChecking", "no"); //Disable host-key checking. You should not do this in production
//            session.setPassword(password);
//            session.connect();
//
//            ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
//            sftpChannel.connect();
//            sftpChannel.get(remoteFile, localDir);
//
//            sftpChannel.exit();
//            session.disconnect();
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
//    }
//}
