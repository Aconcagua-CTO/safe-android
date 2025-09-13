package io.gnosis.data.models

import java.math.BigInteger

object ChainConfiguration {
    
    fun getSupportedChains(): List<Chain> {
        return if (io.gnosis.data.BuildConfig.USE_TESTNET_CHAINS) {
            getTestnetChains()
        } else {
            getMainnetChains()
        }
    }
    
    private fun getMainnetChains(): List<Chain> = listOf(
        createEthereumMainnet(),
        createArbitrumMainnet(),
        createPolygonMainnet(),
        createGnosisMainnet(),
        createRootstockMainnet(),
        createBaseMainnet()
    )
    
    private fun getTestnetChains(): List<Chain> = listOf(
        createSepoliaTestnet(),
        createArbitrumGoerliTestnet(),
        createMumbaiTestnet(),
        createChiadoTestnet(),
        createRootstockTestnet(),
        createBaseSepoliaTestnet()
    )
    
    // ===== MAINNET CHAINS =====
    
    private fun createEthereumMainnet() = Chain(
        chainId = BigInteger.valueOf(1),
        l2 = false,
        name = "Ethereum",
        shortName = "eth",
        textColor = "#001428",
        backgroundColor = "#E8E7E6",
        rpcUri = "https://mainnet.infura.io/v3/",
        rpcAuthentication = RpcAuthentication.API_KEY_PATH,
        blockExplorerTemplateAddress = "https://etherscan.io/address/",
        blockExplorerTemplateTxHash = "https://etherscan.io/tx/",
        ensRegistryAddress = "0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e",
        features = listOf(Chain.Feature.EIP1559)
    ).apply {
        currency = Chain.Currency(
            chainId = BigInteger.valueOf(1),
            name = "Ether",
            symbol = "ETH",
            decimals = 18,
            logoUri = "https://safe-transaction-assets.safe.global/chains/1/currency_logo.png"
        )
    }
    
    private fun createArbitrumMainnet() = Chain(
        chainId = BigInteger.valueOf(42161),
        l2 = true,
        name = "Arbitrum One",
        shortName = "arb1",
        textColor = "#ffffff",
        backgroundColor = "#28A0F0",
        rpcUri = "https://arb1.arbitrum.io/rpc/",
        rpcAuthentication = RpcAuthentication.NO_AUTHENTICATION,
        blockExplorerTemplateAddress = "https://arbiscan.io/address/",
        blockExplorerTemplateTxHash = "https://arbiscan.io/tx/",
        ensRegistryAddress = null,
        features = listOf()
    ).apply {
        currency = Chain.Currency(
            chainId = BigInteger.valueOf(42161),
            name = "Ether",
            symbol = "ETH",
            decimals = 18,
            logoUri = "https://safe-transaction-assets.safe.global/chains/42161/currency_logo.png"
        )
    }
    
    private fun createPolygonMainnet() = Chain(
        chainId = BigInteger.valueOf(137),
        l2 = true,
        name = "Polygon",
        shortName = "matic",
        textColor = "#ffffff",
        backgroundColor = "#8247E5",
        rpcUri = "https://polygon-rpc.com/",
        rpcAuthentication = RpcAuthentication.NO_AUTHENTICATION,
        blockExplorerTemplateAddress = "https://polygonscan.com/address/",
        blockExplorerTemplateTxHash = "https://polygonscan.com/tx/",
        ensRegistryAddress = null,
        features = listOf()
    ).apply {
        currency = Chain.Currency(
            chainId = BigInteger.valueOf(137),
            name = "Matic",
            symbol = "MATIC",
            decimals = 18,
            logoUri = "https://safe-transaction-assets.safe.global/chains/137/currency_logo.png"
        )
    }
    
    private fun createGnosisMainnet() = Chain(
        chainId = BigInteger.valueOf(100),
        l2 = true,
        name = "Gnosis Chain",
        shortName = "gno",
        textColor = "#ffffff",
        backgroundColor = "#48A9A6",
        rpcUri = "https://rpc.gnosischain.com/",
        rpcAuthentication = RpcAuthentication.NO_AUTHENTICATION,
        blockExplorerTemplateAddress = "https://gnosisscan.io/address/",
        blockExplorerTemplateTxHash = "https://gnosisscan.io/tx/",
        ensRegistryAddress = null,
        features = listOf()
    ).apply {
        currency = Chain.Currency(
            chainId = BigInteger.valueOf(100),
            name = "xDAI",
            symbol = "XDAI",
            decimals = 18,
            logoUri = "https://safe-transaction-assets.safe.global/chains/100/currency_logo.png"
        )
    }
    
    private fun createRootstockMainnet() = Chain(
        chainId = BigInteger.valueOf(30),
        l2 = false,
        name = "Rootstock",
        shortName = "rsk",
        textColor = "#ffffff",
        backgroundColor = "#FF6600",
        rpcUri = "https://public-node.rsk.co/",
        rpcAuthentication = RpcAuthentication.NO_AUTHENTICATION,
        blockExplorerTemplateAddress = "https://explorer.rsk.co/address/",
        blockExplorerTemplateTxHash = "https://explorer.rsk.co/tx/",
        ensRegistryAddress = null,
        features = listOf()
    ).apply {
        currency = Chain.Currency(
            chainId = BigInteger.valueOf(30),
            name = "Smart Bitcoin",
            symbol = "RBTC",
            decimals = 18,
            logoUri = "https://safe-transaction-assets.safe.global/chains/30/currency_logo.png"
        )
    }
    
    private fun createBaseMainnet() = Chain(
        chainId = BigInteger.valueOf(8453),
        l2 = true,
        name = "Base",
        shortName = "base",
        textColor = "#ffffff",
        backgroundColor = "#0052FF",
        rpcUri = "https://mainnet.base.org/",
        rpcAuthentication = RpcAuthentication.NO_AUTHENTICATION,
        blockExplorerTemplateAddress = "https://basescan.org/address/",
        blockExplorerTemplateTxHash = "https://basescan.org/tx/",
        ensRegistryAddress = null,
        features = listOf()
    ).apply {
        currency = Chain.Currency(
            chainId = BigInteger.valueOf(8453),
            name = "Ether",
            symbol = "ETH",
            decimals = 18,
            logoUri = "https://safe-transaction-assets.safe.global/chains/8453/currency_logo.png"
        )
    }
    
    // ===== TESTNET CHAINS =====
    
    private fun createSepoliaTestnet() = Chain(
        chainId = BigInteger.valueOf(11155111),
        l2 = false,
        name = "Sepolia",
        shortName = "sep",
        textColor = "#ffffff",
        backgroundColor = "#B8AAD5",
        rpcUri = "https://sepolia.infura.io/v3/",
        rpcAuthentication = RpcAuthentication.API_KEY_PATH,
        blockExplorerTemplateAddress = "https://sepolia.etherscan.io/address/",
        blockExplorerTemplateTxHash = "https://sepolia.etherscan.io/tx/",
        ensRegistryAddress = null,
        features = listOf(Chain.Feature.EIP1559)
    ).apply {
        currency = Chain.Currency(
            chainId = BigInteger.valueOf(11155111),
            name = "Sepolia Ether",
            symbol = "SEP",
            decimals = 18,
            logoUri = "https://safe-transaction-assets.safe.global/chains/11155111/currency_logo.png"
        )
    }
    
    private fun createArbitrumGoerliTestnet() = Chain(
        chainId = BigInteger.valueOf(421613),
        l2 = true,
        name = "Arbitrum Goerli",
        shortName = "arb-goerli",
        textColor = "#ffffff",
        backgroundColor = "#28A0F0",
        rpcUri = "https://goerli-rollup.arbitrum.io/rpc/",
        rpcAuthentication = RpcAuthentication.NO_AUTHENTICATION,
        blockExplorerTemplateAddress = "https://goerli.arbiscan.io/address/",
        blockExplorerTemplateTxHash = "https://goerli.arbiscan.io/tx/",
        ensRegistryAddress = null,
        features = listOf()
    ).apply {
        currency = Chain.Currency(
            chainId = BigInteger.valueOf(421613),
            name = "Arbitrum Goerli Ether",
            symbol = "AGOR",
            decimals = 18,
            logoUri = "https://safe-transaction-assets.safe.global/chains/421613/currency_logo.png"
        )
    }
    
    private fun createMumbaiTestnet() = Chain(
        chainId = BigInteger.valueOf(80001),
        l2 = true,
        name = "Mumbai",
        shortName = "maticmum",
        textColor = "#ffffff",
        backgroundColor = "#8247E5",
        rpcUri = "https://rpc-mumbai.maticvigil.com/",
        rpcAuthentication = RpcAuthentication.NO_AUTHENTICATION,
        blockExplorerTemplateAddress = "https://mumbai.polygonscan.com/address/",
        blockExplorerTemplateTxHash = "https://mumbai.polygonscan.com/tx/",
        ensRegistryAddress = null,
        features = listOf()
    ).apply {
        currency = Chain.Currency(
            chainId = BigInteger.valueOf(80001),
            name = "Mumbai Matic",
            symbol = "MATIC",
            decimals = 18,
            logoUri = "https://safe-transaction-assets.safe.global/chains/80001/currency_logo.png"
        )
    }
    
    private fun createChiadoTestnet() = Chain(
        chainId = BigInteger.valueOf(10200),
        l2 = true,
        name = "Chiado",
        shortName = "chi",
        textColor = "#ffffff",
        backgroundColor = "#48A9A6",
        rpcUri = "https://rpc.chiadochain.net/",
        rpcAuthentication = RpcAuthentication.NO_AUTHENTICATION,
        blockExplorerTemplateAddress = "https://blockscout.chiadochain.net/address/",
        blockExplorerTemplateTxHash = "https://blockscout.chiadochain.net/tx/",
        ensRegistryAddress = null,
        features = listOf()
    ).apply {
        currency = Chain.Currency(
            chainId = BigInteger.valueOf(10200),
            name = "Chiado xDAI",
            symbol = "XDAI",
            decimals = 18,
            logoUri = "https://safe-transaction-assets.safe.global/chains/10200/currency_logo.png"
        )
    }
    
    private fun createRootstockTestnet() = Chain(
        chainId = BigInteger.valueOf(31),
        l2 = false,
        name = "Rootstock Testnet",
        shortName = "trsk",
        textColor = "#ffffff",
        backgroundColor = "#FF6600",
        rpcUri = "https://public-node.testnet.rsk.co/",
        rpcAuthentication = RpcAuthentication.NO_AUTHENTICATION,
        blockExplorerTemplateAddress = "https://explorer.testnet.rsk.co/address/",
        blockExplorerTemplateTxHash = "https://explorer.testnet.rsk.co/tx/",
        ensRegistryAddress = null,
        features = listOf()
    ).apply {
        currency = Chain.Currency(
            chainId = BigInteger.valueOf(31),
            name = "Test Smart Bitcoin",
            symbol = "tRBTC",
            decimals = 18,
            logoUri = "https://safe-transaction-assets.safe.global/chains/31/currency_logo.png"
        )
    }
    
    private fun createBaseSepoliaTestnet() = Chain(
        chainId = BigInteger.valueOf(84532),
        l2 = true,
        name = "Base Sepolia",
        shortName = "base-sep",
        textColor = "#ffffff",
        backgroundColor = "#0052FF",
        rpcUri = "https://sepolia.base.org/",
        rpcAuthentication = RpcAuthentication.NO_AUTHENTICATION,
        blockExplorerTemplateAddress = "https://sepolia.basescan.org/address/",
        blockExplorerTemplateTxHash = "https://sepolia.basescan.org/tx/",
        ensRegistryAddress = null,
        features = listOf()
    ).apply {
        currency = Chain.Currency(
            chainId = BigInteger.valueOf(84532),
            name = "Base Sepolia Ether",
            symbol = "ETH",
            decimals = 18,
            logoUri = "https://safe-transaction-assets.safe.global/chains/84532/currency_logo.png"
        )
    }
}
