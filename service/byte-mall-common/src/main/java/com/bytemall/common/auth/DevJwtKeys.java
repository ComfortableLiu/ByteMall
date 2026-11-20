package com.bytemall.common.auth;

/**
 * 内置开发用JWT密钥对：仅限本地开发的固定回退密钥，禁止用于生产环境。
 * 所有服务共享同一对密钥（user-service用私钥签发、其他服务用公钥验签），
 * 配置了 jwt.rsa.* 或 JWT_RSA_* 环境变量时优先使用配置值。
 */
public final class DevJwtKeys {

    public static final String PRIVATE_KEY_PEM = """
            -----BEGIN PRIVATE KEY-----
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCbu5v5vfQQQLvY
            RUW2a9ut7m+If5YH/DvLaRfka2R89paJQSHZBKIK5t9ARuvr708zhew++JdeeAXt
            QMDqdP5bbxuNd0GbG5/YYmJKKIEU+TxMF629iMX1hHmIU9nsHI8glY+/m9e93pE0
            wQBNo6CZG4bhGHiDbF4mihGsHZwsTtBdf8jgSvBx4N5zjjXa3COURWXtjleBEwOh
            JcHbNdDGiuJsRxn2gtPfK8pG/I6gcQg1Ck3mZD7y7FKVhSW24hiNcQWYZcif/sKQ
            0endAMlxoe78dCd9p4K5fOG8gOOlYE3JEMVOKgLNhm2AjxP3pUTRzO6FrsrlE19G
            Bi2AcBEhAgMBAAECggEAHMMho5Z5pSbPiroAjhiNkiofX05xHzhusrrONtm+zXWd
            h+wHnFVGuEy7ntt6ZzDjp9hsMZUoH8J7Fb6H4sWI1MpIhHAqJcOMWiKRlLC2ZnFB
            uawF4qOILDgzWgJilUERDiuRSQo3ZlHc76lVrQEzgBOdbaVfXKZ/HQ8TBH7TgTsj
            HqTLGyuhPfKsy+UYpTGb2R5+PhyrHBFgnlKiyBepizzsS39t/2+dTE9rEAWcN1RU
            M+HFxOZTcUHYJJXQ3WwL6sUtOLD8/pACJCFVyN/FN3PsPVyiYUpd6CO7ZI0PK0PP
            6BnjLOJ5BJm27JG0JTYzXxuuqNAuSCneW1iRAjRZIQKBgQDbBDpRa+9In30/vHig
            EWTsQCNzaVNVK3lVwjs10Mpowo6JFRAh8jli8+J2UIqCkEQQ1RrGo7f4JgKVf+68
            RCplkHt1Cw9Vpjhom9c/5cyI7jCH6oCGU7lqtA+mp2L+53yeB5P5cDJCMdMUGG0h
            doDNOEGqI6mHx3GBVkYq48rbdwKBgQC2B7f6apI2wz9aY3WYRsugAYNrkQc1P4Jm
            Ti5fe7tnRwV+5Oz/Jat6JGBaPCRLaKfmc0RuldU3PipX74+HU/0K/04Oe3C5X1u9
            h0OMemLSjLmSslXFaqIjz+y7qzAWpiNocJJiezRe1H579b1tXeYwbGM2wHV2CanZ
            w/Mvj4DuJwKBgQC7xEIjL//zKRj+HZzkzkTR7qL1/SR+bkkV5vFQT/JYIgSFLdOo
            5rugRCqw+JfnG7vfGnRcPFwqmJu6g/Wu3lnivR262IKLEXhm5sSBeYgDFQHyZG9P
            ulPKURjh7buHgfpqmWCZh0DvUzv9jHtzJt+CEWrqbKyZRbngApEhkC2IPQKBgDOq
            2TPBECNvJApZ9MT2Q1H1XAIqNiuNDUxQoKUFj9nutMah4AtD+/o9XQP/0iV9XTry
            ONhSuyRlBsvS+aBl3ItEkDlYHFThlj6jqC61hltRClNzPxk7/S3BFObn3kxvd9lr
            KoG9is3n7sbsp4nlfzL7cOXXlv+cGpPvJJW5W9U9AoGAEL4amIKWpDGwicxt18RY
            oheRNIHvt0ccyyTZRj50hZSXc/Rgn1ebzMLy3xLda0KUtUCrfd7AQP1xrrLrUW2G
            wvvnqWAV3FWwdst94TS/d52cX8FNeRfdDlSJaKjvGq4FUvoqbMZgFgvzaHOrJJlZ
            vEhX3aJylMUoorOstPk8yNQ=
            -----END PRIVATE KEY-----
            """;

    public static final String PUBLIC_KEY_PEM = """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAm7ub+b30EEC72EVFtmvb
            re5viH+WB/w7y2kX5GtkfPaWiUEh2QSiCubfQEbr6+9PM4XsPviXXngF7UDA6nT+
            W28bjXdBmxuf2GJiSiiBFPk8TBetvYjF9YR5iFPZ7ByPIJWPv5vXvd6RNMEATaOg
            mRuG4Rh4g2xeJooRrB2cLE7QXX/I4ErwceDec4412twjlEVl7Y5XgRMDoSXB2zXQ
            xoribEcZ9oLT3yvKRvyOoHEINQpN5mQ+8uxSlYUltuIYjXEFmGXIn/7CkNHp3QDJ
            caHu/HQnfaeCuXzhvIDjpWBNyRDFTioCzYZtgI8T96VE0czuha7K5RNfRgYtgHAR
            IQIDAQAB
            -----END PUBLIC KEY-----
            """;

    private DevJwtKeys() {
    }
}
